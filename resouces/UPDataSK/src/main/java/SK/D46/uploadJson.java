package SK.D46;

import SK.SSLClient;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import javafx.scene.input.DataFormat;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * 调用方式 jar uploadJson.jar Properties地址
 */
public class uploadJson {
    public static void main(String[] args){
        Connection conn = null;
        try {
            //Properties properties = readProperty("C:/java/UPLoad/param.properties");
            Properties properties = readProperty(args[0].toString());
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String logName =simpleDateFormat.format(new Date());
            String path_log = properties.getProperty("path_log");
            enableLog(path_log+logName+".log");
            //连接数据库
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            String jdbc_url = properties.getProperty("jdbc_url");
            String username = properties.getProperty("username");
            String password = properties.getProperty("password");
            conn = getConnection(jdbc_url, username, password);
            //拿到需要上传申康病种的jsonObject
            String jsonStringResult = properties.getProperty("jsonStringResult");
            String startTimeStamp = properties.getProperty("startTimeStamp");
            String sql_timestamp = properties.getProperty("sql_timestamp");
            JSONObject timeStampAndPids = getNextTimeStampAndPids(conn,sql_timestamp,startTimeStamp);
            String pids = timeStampAndPids.getString("pids");
            String newStartTimeStamp = timeStampAndPids.getString("timeStamp");
            JSONObject result = JSONObject.parseObject(jsonStringResult);
            String jsonStringTableInfo = properties.getProperty("jsonStringTableInfo");
            JSONArray jsonArrayTableInfo = JSONArray.parseArray(jsonStringTableInfo);
            String timeStampTableName = properties.getProperty("timeStampTableName");
            String key_sm4 = properties.getProperty("key_sm4");
            SetSKJSONObject(conn,result,startTimeStamp,jsonArrayTableInfo,timeStampTableName,pids,key_sm4);
            //上传拿到的数据
            JSONObject response = upLoadResult(result,properties);
            if ("200".equals(response.getString("code"))){
                //最新上传成功startTimeStamp
                properties.setProperty("startTimeStamp",newStartTimeStamp);
                properties.store(new FileWriter(args[0],false),"");
                System.out.println(new Date().toString());
                System.out.println("startTimeStamp:"+startTimeStamp+"endTimeStamp:"+newStartTimeStamp+"本次数据上传成功");
            }else{
                //打印异常日志
                throw new Exception("startTimeStamp:"+startTimeStamp+"endTimeStamp:"+newStartTimeStamp+"本次数据上传失败");
            }
        }catch (Exception e){
            System.out.println("error:"+new Date().toLocaleString());
            System.out.println("error:"+e.toString());
        }finally {
            try {
                conn.close();
            } catch (SQLException throwable) {
                System.out.println("error:"+new Date().toLocaleString());
                System.out.println("error:"+throwable.toString());
            }
        }

    }

    /**
     * 获得下一个startTimeStamp
     * @param sql_timestamp
     * @param startTimeStamp
     * @return
     */
    private static JSONObject getNextTimeStampAndPids(Connection conn,String sql_timestamp, String startTimeStamp) throws SQLException {
        JSONObject result = new JSONObject();
        Statement stm = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        ResultSet rs = stm.executeQuery(sql_timestamp+" and timestamp>"+startTimeStamp);
        String pids = "''";
        while (rs.next()){
            pids=pids+","+"'"+rs.getString("patient_id")+"'";
        }
        rs.last();
        String timeStamp = rs.getString("timestamp");
        if(!timeStamp.startsWith("0x")&&!timeStamp.equals("0")){
            timeStamp="0x"+timeStamp;
        }
        result.put("pids",pids);
        result.put("timeStamp",timeStamp);
        return result;
    }
    /**
     * 获得最终上传的json对象
     *
     */
    private static void SetSKJSONObject(Connection conn, JSONObject result, String startTimeStamp, JSONArray jsonArrayTableInfo, String timeStampTableName,String pids,String key_sm4) throws IOException {
        JSONObject modelInfo = new JSONObject();
        JSONArray modelData = new JSONArray();//存放获得的表数据集
        //遍历需要查询的数据集的参数
        jsonArrayTableInfo.stream().forEach(item ->{
            JSONObject itemObject = new JSONObject();
            JSONObject itemJSON = (JSONObject) item;
            String modelCode = itemJSON.getString("modelCode");
            String description = itemJSON.getString("description");
            String itemsSql = itemJSON.getString("itemsSql");
            String[] SM4Fields = itemJSON.getString("SM4Fields").split(",");
            List<String> SM4FieldsL = Arrays.asList(SM4Fields);
            if (timeStampTableName.equals(modelCode)){//主表用timeStamp
                itemsSql = itemsSql+" and timestamp>"+startTimeStamp;
            }else {//从表用pids
                itemsSql = itemsSql+" and patient_id in ("+pids+")";
            }
            JSONArray itemJson = null;
            try {
                itemJson = loadDBTOJson(conn,itemsSql,startTimeStamp,SM4FieldsL,key_sm4);
            } catch (SQLException throwable) {
                throwable.printStackTrace();
            }
            if(itemJson.size()!=0){
                itemObject.put("modelCode",modelCode);
                itemObject.put("description",description);
                itemObject.put("items",itemJson);
                modelData.add(itemObject);
            }
        });
        result.put("modelData",modelData);
    }
    /**
     * 上传申康专病库数据
     * @param result
     * @param properties
     * @return
     */
    private static JSONObject upLoadResult(JSONObject result, Properties properties) throws Exception {
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.1,SSLv3");
        //先获得token
        String grant_type = properties.getProperty("grant_type");
        String client_id = properties.getProperty("client_id");
        String client_secret = properties.getProperty("client_secret");
        String scope = properties.getProperty("scope");
        String tokenUrl = properties.getProperty("url_token");
        HashMap<String,String> param = new HashMap<>();
        param.put("grant_type",grant_type);
        param.put("client_id",client_id);
        param.put("client_secret",client_secret);
        param.put("scope",scope);
        String token = new SSLClient().getToken(tokenUrl,param,"UTF-8");
        //发送json数据
        String url_model_data = properties.getProperty("url_model_data");
        String s = JSON.toJSONStringWithDateFormat(result,"yyyy-MM-dd HH:mm:ss",SerializerFeature.DisableCircularReferenceDetect);
        String resultString = new SSLClient().doPostJson(url_model_data,s,token,"UTF-8");
        return JSONObject.parseObject(resultString);
    }

    /**
     * DB中拿数据转为json对象
     * @param sql   查询语句
     * @param startTimeStamp    查询起始值
     * @param SM4FieldsL
     * @return  查询此数据集的json对象集合
     */
    private static JSONArray loadDBTOJson(Connection conn, String sql, String startTimeStamp, List<String> SM4FieldsL,String key_sm4) throws SQLException  {
        JSONArray result = new JSONArray();
        try {
            Statement stm = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet rs = stm.executeQuery(sql);
            result = resultSetToJson(rs,SM4FieldsL,key_sm4);
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        return result;
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
     * 通过RS获得对应的json对象
     * @param
     * @param SM4FieldsL
     * @return
     * @throws SQLException
     * @throws JSONException
     */
    public static JSONArray resultSetToJson(ResultSet rs, List<String> SM4FieldsL,String sm4_key) throws Exception {
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
                if(metaData.getColumnTypeName(i).contains("date")){
                    value = value.replaceFirst("\\.0","");
                }

                row_col.put("propertyCode",columnName);
                if(SM4FieldsL.contains(columnName)){
                    //sm4加密了
                    SymmetricCrypto sm4 = SmUtil.sm4(sm4_key.getBytes());
                    value = sm4.encryptHex(value);
                }
                row_col.put("propertyValue",value);
                row.add(row_col);
            }
        rows.add(row);
        }

        return rows;
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
     * 调用api 发送json对象
     * @param url
     * @param json
     * @return
     * @throws IOException
     */
    public static String doPostJson(String url, JSONObject json,String token) throws IOException {

        CloseableHttpResponse response = null;
        String resultString = "";
        try (
                // 创建Httpclient对象
                CloseableHttpClient httpClient = HttpClients.createDefault();
        ) {
            // 创建Http Post请求
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Authorization","Bearer "+token);
            // 创建请求内容
            String s = JSON.toJSONStringWithDateFormat(json,"yyyy-MM-dd HH:mm:ss",SerializerFeature.DisableCircularReferenceDetect);
            StringEntity entity = new StringEntity(s, ContentType.APPLICATION_JSON);

            httpPost.setEntity(entity);
            // 执行http请求
            response = httpClient.execute(httpPost);
            resultString = EntityUtils.toString(response.getEntity(), "UTF-8");
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                throw e;
            }
        }

        return resultString;
    }

    /**
     * 调用 api 获得 token
     * @param url
     * @param param
     * @return
     * @throws IOException
     */
    public static String getToken(String url, Map<String, String> param) throws IOException {

        CloseableHttpResponse response = null;
        String resultString = "";
        try (
                // 创建Httpclient对象
                CloseableHttpClient httpClient = HttpClients.createDefault();
        ) {
            // 创建Http Post请求
            HttpPost httpPost = new HttpPost(url);
            // 创建参数列表
            if (param != null) {
                List<NameValuePair> paramList = new ArrayList();
                for (String key : param.keySet()) {
                    paramList.add(new BasicNameValuePair(key, param.get(key)));
                }
                // 模拟表单
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paramList);
                httpPost.setEntity(entity);
            }
            // 执行http请求
            response = httpClient.execute(httpPost);
            resultString = EntityUtils.toString(response.getEntity(), "UTF-8");
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                throw e;
            }
        }
        JSONObject result = JSONObject.parseObject(resultString);
        return result.getString("access_token");
    }
    /*
        启动日志的方法
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
