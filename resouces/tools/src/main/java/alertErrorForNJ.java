import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * 为南京解析消息模块提供异常检测报错发短信功能
 */
public class alertErrorForNJ {
    public static void main(String[] args) throws Exception {
        //先拿到所有参数
        Properties properties = readProperty("param.properties");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat simpleDateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String logName =simpleDateFormat.format(new Date());
        String lastDayTime = simpleDateFormat1.format(new Date(System.currentTimeMillis()-1000*60*60*24));
        enableLog(logName+".log");
        //拿到当天最新报错数据集
        //1)连接数据库
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        String jdbc_url = properties.getProperty("jdbc_url");
        String username = properties.getProperty("username");
        String password = properties.getProperty("password");
        int subLength = Integer.parseInt(properties.getProperty("subLength"));
        Connection conn = getConnection(jdbc_url, username, password);
        //2)select语句获得ResultSet
        String selectSql = properties.getProperty("selectSql");
        Statement stm = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        ResultSet rs = stm.executeQuery(selectSql+"'"+lastDayTime+"'");
        //启动发短信流程
        JSONArray result = resultSetToJson(rs);
        if(result.size()!=0){
            int length = result.toJSONString().length();
            subLength = length>subLength?subLength:length;
            seedMsg(result.toJSONString().substring(0,subLength),properties);
        }
    }
    /**
     * 通过RS获得对应的json对象
     * @param
     * @return
     * @throws SQLException
     * @throws JSONException
     */
    public static JSONArray resultSetToJson(ResultSet rs) throws Exception {
        // json数组
        JSONArray rows = new JSONArray();

        // 获取列数
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        // 遍历ResultSet中的每条数据
        while (rs.next()) {
            JSONArray row = new JSONArray();
            // 遍历每一列
            for (int i = 1; i <= columnCount; i++) {
                JSONObject row_col = new JSONObject();
                String columnName =metaData.getColumnLabel(i);
                String value = rs.getString(columnName);
                row_col.put(columnName,value);
                row.add(row_col);
            }
            rows.add(row);
        }

        return rows;
    }
    /**
     * 发送短信
     * @param msg
     */
    static void seedMsg(String msg,Properties properties){
        String iphones = properties.getProperty("iphones");
        String appkey = properties.getProperty("appkey");
        String appsecret = properties.getProperty("appsecret");
        String getUrl = properties.getProperty("getUrl");
        String postUrl = properties.getProperty("postUrl");
        HashMap<String,Object> param = new HashMap<>();
        param.put("appkey",appkey);
        param.put("appsecret",appsecret);
        //先获得访问权限
        JSONObject result = JSONObject.parseObject(HttpUtil.get(getUrl,param));
        if("success".equals(result.get("msg"))){
            //获得发送短信token
            String token = result.getJSONObject("data").getString("token");
            JSONObject paramMap = new JSONObject();
            paramMap.put("msgTypeId","1");
            JSONArray ja = new JSONArray();
            JSONObject jo = new JSONObject();
            jo.put("channelCode","2");
            jo.put("receivedUserType","");
            jo.put("receivedUserNo","");
            jo.put("tel",iphones);
            JSONObject jo1 = new JSONObject();
            jo1.put("templateId","1");
            jo1.put("content",msg);
            jo.put("template",jo1);
            ja.add(jo);
            paramMap.put("channels",ja);
            String responseString = HttpRequest.post(postUrl)
                    .header("token", token)
                    .body(paramMap.toJSONString())
                    .execute().body();
            System.out.println(responseString);
        }else {
            System.out.println(new Date()+":今日未获得访问权限！");
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
        }
        return conn;
    }
    /**
     * 找到配置文件
     * @param config_path
     */
    private static Properties readProperty(String config_path) throws IOException {
        Properties properties = new Properties();
        BufferedReader bufferedReader = new BufferedReader(new FileReader(config_path));
        try {
            properties.load(bufferedReader);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            bufferedReader.close();
        }
        return  properties;
    }
    /**
    * 启动日志的方法
    */
    public static void enableLog(String path){
        try {
            //指向一个日志文件
            PrintStream out=new PrintStream(new FileOutputStream(path,true));
            //改变输出方向 (默认是输出到控制台, 所以需要修改输出方向)
            System.setOut(out);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
