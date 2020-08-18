package PKI.Tools;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

public class getFileAttrs {
    public static void main(String[] args) throws JSONException, IOException {
        JSONArray result = new JSONArray();
        //拿到文件夹下文件属性存入List集合
        SimpleDateFormat df = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS");
        SimpleDateFormat df1 = new SimpleDateFormat("YYYYMMddHHmmss");
        String logsPath = System.getProperty("logPath");//存放的日志文件夹路径
        String dirPath = System.getProperty("dirPath");//需要监控的文件夹路径
        String filesAttrsPath  = System.getProperty("filesAttrsPath");//存放文件属性结果的文件路径
        String logPath = logsPath + df1.format(new Date())+".log";
        //配置日志写到文件中
        System.setOut(new PrintStream(logPath));
        File file = new File(dirPath);
        if(!file.isDirectory()){
            System.out.println("参数错误，请输入文件夹路径");
        }else{
            String[] files=file.list();
            for(int i=0;i<files.length;i++){
                JSONObject jo = new JSONObject();
                File readFile = new File(dirPath+files[i]);
                String fileName = readFile.getName();
                String lastModifyTime = df.format(new Date(readFile.lastModified()));
                jo.put("fileName",fileName);
                jo.put("lastModifyTime",lastModifyTime);
                result.add(jo);
            }
        }
        //写入文件中
        File filesAttrs = new File(filesAttrsPath);
        Writer out = new FileWriter(filesAttrs);
        for(int i=0;i<result.size();i++){
            JSONObject jo = result.getJSONObject(i);
            String fileName = jo.getString("fileName");
            String lastModifyTime = jo.getString("lastModifyTime");
            out.write(fileName+"\t"+lastModifyTime+"\r\n");
        }
        System.out.println(df.format(new Date())+"：成功");
        out.close();
    }
}
