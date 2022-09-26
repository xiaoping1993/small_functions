package PKI.Tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

@SpringBootApplication
public class StructDocxToDbApplication {

	public static void main(String[] args) throws Exception {
		setSystemOutPrintLnToFile();
		Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		String pathSource = System.getProperty("sourcePath");//"C:\\Users\\wangj01052\\Desktop\\test";
		String url = System.getProperty("url");//"jdbc:sqlserver://127.0.0.1:1433;DatabaseName=PKI_KY";
		String username = System.getProperty("username");//"sa";
		String password = System.getProperty("password");//"!@34QWer";
		int count = structDocxToDB(pathSource,url,username,password);
		//打印下本次操作了多少文件
		System.out.println(new Date().toString()+"执行了"+count+"个文件");
	}
	
	/**
	 * 将System.out.println输出写到文件中
	 */
	static void setSystemOutPrintLnToFile(){
		PrintStream out;
		try {
			String dir =System.getProperty("user.dir");
			FileOutputStream outputStream = new FileOutputStream(dir+"\\log.txt",true);
			out = new PrintStream(outputStream);
			System.setOut(out);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			System.out.println(new Date().toString()+e.toString());
		}
	}
	//结构化指定文件夹下docx到数据库中
	private static int structDocxToDB(String pathSource,String url,String username,String password) throws SQLException{
		Integer count = 0;
		Connection connection = getConnection(url,username,password);
		connection.setAutoCommit(false);
		//找文件夹中docx
		FilenameFilter filefilter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				// TODO Auto-generated method stub
				return name.endsWith(".docx");
			}
		};
		File file = new File(pathSource);
		File[] files = file.listFiles(filefilter);//找到所有docx文件
		for (File f : files) {//便利所有的docx
			JSONArray ja = new JSONArray();
			try{
				ja = getJSONArrayFromDocx(f);
			}catch (Exception e){
				System.out.println(e.toString());
				continue;
			}
			//将ja拿到的结构化数据存入数据库
			if(setJSONArrayToDb(ja,connection)){
				count++;
			};

		}
		connection.close();
		return count;
	}
	/**
	 * 获得指定docx的结构化jsonArray数据
	 * @param f
	 * @return
	 * @throws IOException 
	 */
	private static JSONArray getJSONArrayFromDocx(File f) throws IOException{
		JSONObject tableNames = getTableNames();
		JSONArray ja = new JSONArray();
		String filePath = f.getAbsolutePath();
		String fileName = f.getName();
		long modifiedTime = f.lastModified();
		Date date=new Date(modifiedTime);
		SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:MM:SS");
		String fileModifyTime=sdf.format(date);
		ja.add(0, fileName);
		ja.add(1, filePath);
		ja.add(2,fileModifyTime);
		JSONObject jo = new JSONObject();
		//结构化数据存入jo中
		FileInputStream fis = new FileInputStream(f);
		XWPFDocument doc = new XWPFDocument(fis);
		List<XWPFTable> tables = doc.getTables();
		for(int t=0;t<tables.size();t++){
			XWPFTable table = tables.get(t);
			List<XWPFTableRow> rows = table.getRows();
			XWPFTableRow firstRow = rows.get(0);
			if(t==0){//第一个表特殊处理,表结构不一样
				for (int i = 1; i < rows.size(); i++) {
					List<XWPFTableCell> cells = rows.get(i).getTableCells();
					for(int j = 0;j<cells.size();j=j+2){
						jo.put(cells.get(j).getText().trim(), cells.get(j+1).getText().trim());
					}
				}
			}else if(t==1){//第二张表特殊处理，表结构不一样
				for (int i = 2; i < rows.size(); i++) {
					List<XWPFTableCell> cells = rows.get(i).getTableCells();
					XWPFTableRow secondRow = rows.get(1);
					for(int j = 1;j<cells.size();j++){
						jo.put(rows.get(i).getCell(0).getText().trim()+":"+firstRow.getCell(j).getText().trim()+secondRow.getCell(j).getText().trim()+"("+tableNames.getString(Integer.toString(t))+")", cells.get(j).getText().trim());
					}
				}
			}else{
				for (int i = 1; i < rows.size(); i++) {
					List<XWPFTableCell> cells = rows.get(i).getTableCells();
					for (int j = 1; j < cells.size(); j++) {
						jo.put(rows.get(i).getCell(0).getText().trim()+":"+firstRow.getCell(j).getText().trim()+"("+tableNames.getString(Integer.toString(t))+")", cells.get(j).getText().trim());
					}
				}
			}
		}
		//结构化数据存入jo中结束
		ja.add(3,jo);
		return ja;
	}
	/**
	 * 将JSONArray数据存入数据库
	 * @param ja
	 */
	private static boolean setJSONArrayToDb(JSONArray ja,Connection connection){
		Savepoint savepoint = null;
		String fileName = ja.getString(0);
		String filePath = ja.getString(1);
		String fileModifyTime = ja.getString(2);
		JSONObject jo = ja.getJSONObject(3);
		Iterator<String> keys = jo.keySet().iterator();
		try {
			savepoint = connection.setSavepoint();//在处理这个结构化数据时设一个回退点
			String sqlString = "insert into OSAHS_PSGData(fileName,filePath,fileModifyTime,structKey,structValue,structKeyCount,operateTime) values (?,?,?,?,?,?,?)";
			PreparedStatement ps = connection.prepareStatement(sqlString);
			int sum = 0;
			while(keys.hasNext()){
				sum++;
				String key = keys.next();
				String value = jo.getString(key);
				//可以拼接sql字符串了
				ps.setString(1, fileName);
				ps.setString(2, filePath);
				ps.setString(3, fileModifyTime);
				ps.setString(4, key);
				ps.setString(5, value);
				ps.setString(6, Integer.toString(sum));//增加唯一键，好插入数据
				ps.setDate(7, new java.sql.Date(new Date().getTime()));
				//再添加一次预定义参数
				ps.addBatch();
			}
			//批量执行预定义sql
			ps.executeBatch();
			connection.commit();
			return true;
		} catch (Exception e) {
			try {
				connection.rollback(savepoint);
				System.out.println(new Date().toString()+"：文件"+filePath+"结构化失败已回退");
				System.out.println(new Date().toString()+e.toString());
				return false;
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				System.out.println(new Date().toString()+e1.toString());
				return false;
			}
		}
	}
	private static Connection getConnection(String url, String username, String password) {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url,username,password);
		} catch (Exception e) {
			System.out.println(new Date().toString()+e.toString());
		}
		return conn;
	}
	/**
	 * 找到指定docx结构化获得结构化数据集JSONArray
	 * @return
	 */
	static JSONArray getStructDataFromDocx(String sourcePath){
		JSONArray ja = new JSONArray();
		return ja;
	}
	private static JSONObject getTableNames(){
		JSONObject jo = new JSONObject();
		jo.put("0", "一般信息-");
		jo.put("1", "睡眠分期-");
		jo.put("2", "睡眠呼吸暂停事件-中枢性呼吸暂停-");
		jo.put("3", "睡眠呼吸暂停事件-阻塞性呼吸暂停-");
		jo.put("4", "睡眠呼吸暂停事件-混合性呼吸暂停-");
		jo.put("5", "睡眠呼吸暂停事件-低通气-");
		jo.put("6", "呼吸紊乱指数-");
		jo.put("7", "血氧分布-");
		jo.put("8", "微觉醒-");
		jo.put("9", "体位与呼吸事件关系-");
		return jo;
	}
}
