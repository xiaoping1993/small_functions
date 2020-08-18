package PKI.Tools;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class setFilePathsToDBApplication {

	public static void main(String[] args) throws Exception {
		String pathSource = "";
		pathSource = System.getProperty("sourcePath");
		String url = "jdbc:sqlserver://172.30.100.145:1433;DatabaseName=PKI_KY";
		String username = "sa";
		String password = "Perkinelmer123$";
		clearFilePathsFromDB(url,username,password);
		setFilePathsToDB(pathSource,url,username,password);
		
	}
	private static void clearFilePathsFromDB(String url,String username,String password) throws SQLException{
		Connection connection = getConnection(url,username,password);
		Statement stat = connection.createStatement();
		String clearSql = "truncate table pdfPaths";
		stat.execute(clearSql);
		connection.close();
	}
	private static void setFilePathsToDB(String pathSource,String url,String username,String password) throws SQLException{
		Connection connection = getConnection(url,username,password);
		File file = new File(pathSource);
		List<File> files = listFiles(file);
		String insertSql = "insert into pdfPaths(filePath) values(?)";
		PreparedStatement ps = connection.prepareStatement(insertSql);
		for (File f : files) {//便利所有的docx
			String filePath = f.getAbsolutePath();
			if(filePath.toLowerCase().endsWith(".pdf")){//过滤所有的pdf文件
				ps.setString(1, filePath);
				ps.addBatch();
			}
		}
		ps.executeBatch();
		connection.close();
	}
	private static List<File> listFiles(File file){
        List<File> fileList = new ArrayList<>();
        if (file.isDirectory()){
            for (File listFile : file.listFiles()) {
                fileList.addAll(listFiles(listFile));
            }
        }else {
            fileList.add(file);
        }
        return fileList;
    }
	private static Connection getConnection(String url, String username, String password) {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url,username,password);
		} catch (Exception e) {
			System.out.println(e.toString());
		}
		return conn;
	}
}
