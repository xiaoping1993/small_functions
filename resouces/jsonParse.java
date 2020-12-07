
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pkuhit.iihip.sdk.core.Result;
import com.pkuhit.iihip.sdk.exception.ConnectException;
import com.pkuhit.iihip.sdk.message.Client;

public class jsonAnalysis {
	public static void main(String[] args) throws Exception {
		String typeCode = args[0];//"BS001";//System.getProperty("typeCode");//
		String typeName = args[1];//"挂号信息服务";//System.getProperty("typeName");//
		setSystemOutPrintLnToFile(typeCode);
		Client client = ClientHolder.getClient();
		Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		while (true) {
			try {
				// 取消息接口, 不需要返回ACK, 调用接口后队列中的消息会被删除
				Result result = client.receiveMsg(typeCode, false /* 是否返回附件 */, null /* 消息原始文本的模板编码, null不返回文本 */);
				if (result.getMsgStatus() == Result.SUCCESS) {
					String msgId =result.getMsgId();
					System.out.println("取消息成功, msgId:" + msgId);
					// 消息结果集
					Map<String, Object> receivedMessage = result.getMsg();
					String url_from = "jdbc:sqlserver://192.170.1.126:1433;DatabaseName=PKI_Data";
					String username = "sa";
					String password = "Sa123";
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
					String createTime = sdf.format(new Date());
					Connection connection = null;
					Savepoint savepoint = null;
					try {
						connection = getConnection(url_from, username, password);
						connection.setAutoCommit(false);
						savepoint = connection.setSavepoint();
						//插入原始数据（一次操作）
						Statement stm = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
						String message = receivedMessage.toString();
						String insertSql = "insert into JsonOriginal(msgId,message,typeCode,typeName,createTime) values('"+msgId+"','"+message+"','"+typeCode+"','"+typeName+"','"+createTime+"')";
						stm.execute(insertSql);
						insertSql = "insert into JsonAnalysisResult(msgId,fileName,fileValue,jsonPath,groupCount,customOnlyKey) values('"+msgId+"',?,?,?,?,?)";
						PreparedStatement pst =connection.prepareStatement(insertSql);
						//插入解析出来的数据（多次操作）
						readMessage(msgId,receivedMessage,"/",0,pst);
						pst.executeBatch();
						connection.commit();
						connection.close();
						System.out.println("解析消息成功,msgId:"+msgId);
					} catch (Exception e) {
						System.out.println(e.toString());
						try {
			                connection.rollback(savepoint);
			                connection.commit();
			                System.out.println("解析消息失败,msgId:"+msgId+"");
			            } catch (SQLException e1) {
			                System.out.println("SQLException in rollback" + e1.getMessage());
			            }
					}
					
				} else if (result.getMsgStatus() == Result.NO_MESSAGE) {
					// 队列无可用消息, 线程休眠
                    Thread.sleep(10 * 1000);
				} else {
					// 取消息异常
					System.out.println(result.getErrorCode() + ":" + result.getErrorInfo());
                    // Thread.Sleep(10 * 1000);
				}
			} catch (ConnectException e) {
				e.printStackTrace();
				// 客户端与服务器连接中断, 尝试重连
                client.reconnect();
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println(e.toString());
			}
		}
	}
	/**
	 * 核心解析Map对象不管多深，每个节点都写到数据库中（以竖表形式保存）
		解析出来的表结构如下：
		放在本页最下层
	 */
	@SuppressWarnings("unchecked")
	private static void readMessage(String msgId,Map<String, Object> receivedMessage,String jsonPath,Integer groupCount,PreparedStatement pst) {
        try {
        	Set<Map.Entry<String, Object>> entries = receivedMessage.entrySet();
        	for (Map.Entry<String, Object> entry : entries) {
				String keyName = entry.getKey();
				Object keyValue = entry.getValue();
				if (keyValue instanceof Map) {//节点是map对象
					String jsonPath1 = jsonPath+keyName+"/";
					Map<String, Object> map =(Map<String, Object>) keyValue; 
					readMessage(msgId,map,jsonPath1,groupCount,pst);
				}else if (keyValue instanceof List){//节点是list对象
					List<Map<String,Object>> list = (List<Map<String,Object>>) keyValue;
					for (int i = 0; i < list.size(); i++) {
						Integer groupCount1 = i;
						Map<String, Object> map = list.get(i);
						String jsonPath1 = jsonPath+keyName+"/";
						readMessage(msgId,map,jsonPath1,groupCount1,pst);
					}
				}else {
					String jsonPath1 = jsonPath+keyName;
					String keyValue1 = (keyValue==null?"":keyValue.toString());
					//写入数据库
					String customOnlyKey = msgId+keyName+keyValue1+jsonPath1+groupCount;
					try {
						pst.setString(1, keyName);
						pst.setString(2, keyValue1);
						pst.setString(3, jsonPath1);
						pst.setInt(4, groupCount);
						pst.setString(5, customOnlyKey);
						pst.addBatch();
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.out.println(e.toString());
					}
				}
			}
			/*receivedMessage.forEach((k,v)->{
				if (v instanceof Map) {//节点是map对象
					String jsonPath1 = jsonPath+k+"/";
					Map<String, Object> map =(Map<String, Object>) v; 
					readMessage(msgId,map,jsonPath1,groupCount,pst);
				}else if (v instanceof List){//节点是list对象
					List<Map<String,Object>> list = (List<Map<String,Object>>) v;
					for (int i = 0; i < list.size(); i++) {
						Integer groupCount1 = i;
						Map<String, Object> map = list.get(i);
						String jsonPath1 = jsonPath+k+"/";
						readMessage(msgId,map,jsonPath1,groupCount1,pst);
					}
				}else {
					String jsonPath1 = jsonPath+k;
					Integer groupCount1 = groupCount;
					String msgId1 = msgId;
					//写入数据库
					String customOnlyKey = msgId1+k.toString()+v.toString()+jsonPath1+groupCount1;
					try {
						pst.setString(1, k.toString());
						pst.setString(2, v.toString());
						pst.setString(3, jsonPath1);
						pst.setInt(4, groupCount1);
						pst.setString(5, customOnlyKey);
						//pst.execute();
						pst.addBatch();
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.out.println(e.toString());
					}
				}
			});*/
			
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.toString());
		}
	}
    /**
     * 由于这里代码比较特殊之后想要单独打包成jar工具包，所有这里的jdbc就用原生的
     */
    static Connection getConnection(String url, String username, String password) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, username, password);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.toString());
        }
        return conn;
    }

    /**
     * 将System.out.println输出写到文件中
     */
    static void setSystemOutPrintLnToFile(String typeCode) {
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    	String now= sdf.format(new Date());
        PrintStream out;
        try {
            String dir = System.getProperty("user.dir");
            String logPath = dir+"\\"+typeCode;
            File file = new File(logPath);
            if (!file.exists()){
            	file.mkdir();
            }
            out = new PrintStream(logPath + "\\jsonAnalysis_"+now+"log.txt");
            System.setOut(out);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}





CREATE TABLE [dbo].[JsonOriginal](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[msgId] [nvarchar](100) NULL,
	[message] [nvarchar](max) NULL,
	[typeCode] [nvarchar](100) NULL,
	[typeName] [nvarchar](100) NULL,
	[createTime] [datetime] NULL,
	[timestamp] [timestamp] NULL,
 CONSTRAINT [PK_JsonOriginal] PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX  = OFF, STATISTICS_NORECOMPUTE  = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS  = ON, ALLOW_PAGE_LOCKS  = ON) ON [PRIMARY],
 CONSTRAINT [msgIdUnique] UNIQUE NONCLUSTERED 
(
	[msgId] ASC
)WITH (PAD_INDEX  = OFF, STATISTICS_NORECOMPUTE  = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS  = ON, ALLOW_PAGE_LOCKS  = ON) ON [PRIMARY]
) ON [PRIMARY]

CREATE TABLE [dbo].[JsonAnalysisResult](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[msgId] [nvarchar](100) NOT NULL,
	[fileName] [nvarchar](100) NOT NULL,
	[fileValue] [nvarchar](100) NOT NULL,
	[jsonPath] [nvarchar](100) NOT NULL,
	[groupCount] [int] NOT NULL,
	[customOnlyKey] [varchar](800) NOT NULL,
	[timestamp] [timestamp] NOT NULL,
 CONSTRAINT [PK_JsonAnalysisResult] PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX  = OFF, STATISTICS_NORECOMPUTE  = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS  = ON, ALLOW_PAGE_LOCKS  = ON) ON [PRIMARY],
 CONSTRAINT [customOnlyKeyUnique] UNIQUE NONCLUSTERED 
(
	[customOnlyKey] ASC
)WITH (PAD_INDEX  = OFF, STATISTICS_NORECOMPUTE  = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS  = ON, ALLOW_PAGE_LOCKS  = ON) ON [PRIMARY]
) ON [PRIMARY]
GO
SET ANSI_PADDING OFF
GO
/****** Object:  ForeignKey [FK_JsonOriginal_JsonAnalysisResult]    Script Date: 12/02/2020 10:12:14 ******/
ALTER TABLE [dbo].[JsonAnalysisResult]  WITH CHECK ADD  CONSTRAINT [FK_JsonOriginal_JsonAnalysisResult] FOREIGN KEY([msgId])
REFERENCES [dbo].[JsonOriginal] ([msgId])
GO
ALTER TABLE [dbo].[JsonAnalysisResult] CHECK CONSTRAINT [FK_JsonOriginal_JsonAnalysisResult]
GO

