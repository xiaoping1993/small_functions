import oracle.jdbc.OracleConnection;
import oracle.sql.CLOB;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 解析武汉数据库xml字段
 */
public class parseEmrForWH {
    public static void main(String[] args) throws Exception {
        //通用配置
        Properties properties = readProperty("paramWH.properties");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String logName =simpleDateFormat.format(new Date());
        enableLog(logName+".log");
        //读取数据库数据
        //1)连接数据库
        Class.forName("oracle.jdbc.OracleDriver");
        String jdbc_url = properties.getProperty("jdbc_url");
        String username = properties.getProperty("username");
        String password = properties.getProperty("password");
        Connection conn = getConnection(jdbc_url, username, password);
        Connection conn2 = getConnection(jdbc_url, username, password);
        //这里需要注意的是，你对数据库做了几次操作，conn最好还是再来一个，之前操作可能会报空指针异常
        //1）创建结构化数据表
        createTable(properties,conn);
        //2）插入结构化后的表数据
        insertTable(properties,conn,conn2);
        conn.close();
        conn2.close();
    }

    /**
     * insert表数据
     * @param properties
     * @throws SQLException
     * @throws IOException
     * @throws DocumentException
     */
    private static void insertTable(Properties properties,Connection conn1,Connection conn2) throws SQLException, IOException, DocumentException {
        //2）开始正式解析
        String selectSql = properties.getProperty("selectSql");
        Statement stm = conn1.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
        Statement stm2 = conn2.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
        ResultSet resultSet = stm.executeQuery(selectSql);
        String tableNameNew = properties.getProperty("tableNameNew");
        String columnName_focus = properties.getProperty("columnName");
        String column_struct = properties.getProperty("column_struct_dq");
        String column_panel = properties.getProperty("column_panel");
        String[] column_structs = column_struct.split(",");
        String[] column_panels = column_panel.split(",");
        while (resultSet.next()) {
            Document document = null;
            try {
                java.sql.Clob clob = resultSet.getClob(columnName_focus);
                BufferedReader bufferedReader = new BufferedReader(clob.getCharacterStream());
                String structString = bufferedReader.readLine();
                document = DocumentHelper.parseText(structString);
            }catch (Exception e){
                System.out.println(resultSet.getString(1)+":"+e.toString());
                continue;
            }
            try{
                Element root = document.getRootElement();
                Element structs = root.element("structs");
                String columnNames = "";
                String columnValues = "";
                String columnValuesZW = "";
                ResultSetMetaData metaData=resultSet.getMetaData();
                int count = metaData.getColumnCount();
                for (int i =1;i<=count;i++){
                    String columnName = metaData.getColumnName(i);
                    if(!columnName.equalsIgnoreCase(columnName_focus)){
                        String value = resultSet.getString(columnName);
                        columnNames+=columnName+",";
                        columnValues+="'"+value+"',";
                        columnValuesZW+="?,";
                    }
                }
                conn2.setAutoCommit(false);
                String columnNamesOr = columnNames;
                String columnValuesOr = columnValues;
                String columnValuesZWOr = columnValuesZW;
                //上面将columnNames、columnValues将原始表数据都填入了，下面要做的是增加的字段内容
                Iterator iterator = structs.elementIterator();
                int SerialNo=1;
                while (iterator.hasNext()){
                    Element  struct = (Element)iterator.next();
                    for (int i = 0;i<column_structs.length;i++){
                        String columnName = column_structs[i];
                        String value = struct.attributeValue(columnName)==null?"":struct.attributeValue(columnName);
                        if(columnName.equalsIgnoreCase("idpath")){
                            if(!value.equals("")){
                                String[] values = value.split("\\.");
                                for (int j =0;j<values.length;j++){
                                    columnNames+="parentid"+(j+1)+",";
                                    columnValues+="'"+values[j]+"',";
                                }
                            }
                        }else {
                            columnNames+=columnName+",";
                            columnValues+="'"+value+"',";
                        }
                    }
                    columnNames+="type,innerSerial";
                    columnValues+="'struct',"+SerialNo;
                    SerialNo++;
                    //编写插行语句
                    String insertSql = "insert into "+ tableNameNew+"("+columnNames+") values ("+columnValues+")";
                    stm2.executeUpdate(insertSql);
                    //解析完一个节点，就将变量初始化为原始表信息
                    columnNames = columnNamesOr;
                    columnValues = columnValuesOr;
                }
                //为了解决insert超大字符串，prepare来实现
                Element panels = root.element("panels");
                Iterator iterator1 = panels.elementIterator();
                int SerialNo1=1;
                //找到clob在哪个字段哪个位置
                int clobNumber = 0;
                while (iterator1.hasNext()){
                    CLOB clob = new CLOB((OracleConnection)conn2);
                    clob = oracle.sql.CLOB.createTemporary((OracleConnection)conn2,true,1);
                    int beginNumber = columnNames.split(",").length;
                    Element  panel = (Element)iterator1.next();
                    String columnValuess = "";//value的值作为后面，跟初始化value分开
                    for (int i = 0;i<column_panels.length;i++){
                        String columnName = column_panels[i];
                        String value = panel.attributeValue(columnName)==null?"":panel.attributeValue(columnName);
                        if(columnName.toLowerCase(Locale.ROOT).equals("panelText".toLowerCase(Locale.ROOT))){
                            //如果字段是panelText clob的话，需要特殊处理
                            clob.setString(1,value.replace("'",""));
                            clobNumber = i+beginNumber;
                        }

                        columnNames+=columnName+",";
                        columnValuess+="'"+value+"'&&";
                        columnValuesZW+="?,";
                    }
                    columnNames+="type,innerSerial";
                    columnValuess+="'panel'&&"+SerialNo1;
                    columnValuesZW+="?,?";
                    SerialNo1++;
                    //编写插行语句
                    String insertSql = "insert into "+ tableNameNew+"("+columnNames+") values ("+columnValuesZW+")";
                    PreparedStatement preparedStatement = conn2.prepareStatement(insertSql);
                    List<String> columnValuesS = Stream.of(columnValues.split(",")).collect(Collectors.toList());
                    List<String> columnValuesS1 = Stream.of(columnValuess.split("&&")).collect(Collectors.toList());
                    columnValuesS.addAll(columnValuesS1);
                    for(int i=0;i<columnValuesS.size();i++){
                        String value = columnValuesS.get(i).replace("'","");
                        if(i==clobNumber){
                            preparedStatement.setClob(i+1,clob);
                        }else {
                            preparedStatement.setString(i+1,value);
                        }

                    }
                    clob.setString(1,"");
                    preparedStatement.execute();
                    preparedStatement.close();
                    //stm2.executeUpdate(insertSql);
                    //解析完一个节点，就将变量初始化为原始表信息
                    columnNames = columnNamesOr;
                    columnValues = columnValuesOr;
                    columnValuesZW = columnValuesZWOr;
                }
                conn2.commit();
            }catch (Exception e){
                System.out.println(resultSet.getString(1)+":"+e.toString());
                conn2.rollback();
                continue;
            }

        }
    }
    private static HashSet createTable(Properties properties, Connection conn) throws SQLException {
        String selectSql = properties.getProperty("selectSql");
        Statement stm = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
        ResultSet resultSet = stm.executeQuery(selectSql);
        //解析数据集，将xml字段解析插入新表中
        String columnName = properties.getProperty("columnName");
        String tableNameNew = properties.getProperty("tableNameNew");
        //1)创建新表
        //1.1）获得新增加columnNames
        String column_struct = properties.getProperty("column_struct");
        String column_panel = properties.getProperty("column_panel");
        HashSet<String> columnsAdd = new HashSet<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCountOr = metaData.getColumnCount();
        for (int i=1;i<=columnCountOr;i++){
            String columnNameInn = metaData.getColumnName(i);
            if(!columnNameInn.equalsIgnoreCase(columnName)){
                columnsAdd.add(columnNameInn);
            }
        }
        //1.2获得原始columnOri丢去待解析字段
        columnsAdd.addAll(Arrays.asList(column_struct.split(",")));
        columnsAdd.addAll(Arrays.asList(column_panel.split(",")));
        //1.3开始构建createTable脚本创建结构化表sql语句
        StringBuilder createSql = new StringBuilder("create table "+tableNameNew);
        createSql.append("(");
        //1.4上面内部column处理结束下面就是处理后面新增的column了
        Iterator iterator = columnsAdd.iterator();
        while (iterator.hasNext()){
            String columnNameInn = (String) iterator.next();
            if(columnNameInn.toLowerCase(Locale.ROOT).equals("panelText".toLowerCase(Locale.ROOT))){
                createSql.append(columnNameInn+" clob,");
            }else {
                createSql.append(columnNameInn+" varchar2(4000),");
            }
        }
        //1.5额外增加的column(type，innerSerial)
        createSql.append("type varchar2(1000) not null,innerSerial varchar2(1000) not null");
        createSql.append(")");
        //1.6执行创建脚本
        String isExist = "select * from "+tableNameNew;
        try {
            stm.executeQuery(isExist);
        }catch (Exception e){
            if(e.toString().contains("ORA-00942")){
                stm.execute(createSql.toString());
            }
        }
        columnsAdd.add("type");
        columnsAdd.add("innerSerial");
        return columnsAdd;
    }

    /**
     * 连接数据库
     * @param jdbc_url
     * @param username
     * @param password
     * @return
     */
    private static Connection getConnection(String jdbc_url, String username, String password) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(jdbc_url, username, password);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return conn;
    }

    /**
     * 日志写道文件中
     * @param logPath
     */
    private static void enableLog(String logPath) throws FileNotFoundException {
        PrintStream printStream = new PrintStream(new FileOutputStream(logPath));
        System.setOut(printStream);
    }

    /**
     * 加载配置文件
     * @param config_path
     * @return
     * @throws IOException
     */
    private static Properties readProperty(String config_path) throws IOException {
        Properties properties = new Properties();
        BufferedReader bufferedReader = new BufferedReader(new FileReader(config_path));
        properties.load(bufferedReader);
        return properties;
    }
    //数据库中拿到数据
    //
}
