package PKI.Tool.job;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
 
public class FileToZip {
	public static TreeSet<String> ts = new TreeSet<String>();

	public static void ZipFolderMethod(String sFoderName, String zipFolderName) throws IOException {
		Date now = new Date(); 
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");//可以方便地修改日期格式
		String nowFormat = dateFormat.format(now); 
		File sFolder = new File(sFoderName);
		String Name = sFolder.getName();
		String ZipName = Name+"_"+nowFormat+".zip";
		File zipFolder = new File(zipFolderName+"\\"+ZipName);
		ZipOutputStream zipoutFolder = new ZipOutputStream(new FileOutputStream(zipFolder));
		InputStream in = null;
		// zipoutFolder.setEncoding("GBK"); //为解决注释乱码
		zipoutFolder.setComment("文件夹的压缩");

		// 列出所有文件的路径，保存到集合中，在ListAllDirectory(sFoder)方法中用到递归
		TreeSet<String> pathTreeSet = ListAllDirectory(sFolder);


		String[] pathStr = pathTreeSet.toString().substring(1, pathTreeSet.toString().length() - 1).split(",");


		for (int i = 0; i < pathStr.length; i++) {
			String filePath = pathStr[i].trim();
			StringBuffer pathURL = new StringBuffer();
			String[] tempStr = filePath.split("\\\\"); // 这个地方需要注意，在Java中需要“\\\\”表示“\”字符串。


			// 这里的变量j是从第几层开始打压缩包
			for (int j = 6; j < tempStr.length - 1; j++) {
				pathURL.append(tempStr[j] + File.separator);
			}
			String path = pathURL.append(tempStr[tempStr.length - 1]).toString();


			in = new FileInputStream(new File(filePath));


			zipoutFolder.putNextEntry(new ZipEntry(path));


			int temp = 0;
			while ((temp = in.read()) != -1) {
				zipoutFolder.write(temp);
			}


			in.close();
		}
		zipoutFolder.close();
	}


	public static TreeSet<String> ListAllDirectory(File sFolder) {
		if (sFolder != null) {
			if (sFolder.isDirectory()) {
				File f[] = sFolder.listFiles();
				if (f != null) {
					for (int i = 0; i < f.length; i++) {
						ListAllDirectory(f[i]);
					}
				}
			} else {
				ts.add(sFolder.toString());
			}
		}
		return ts;
	}

	/**
	 * 清理文件名显示的日期为上个月的
	 * @param toForderName
	 */
	public static void ClearForder(String toForderName) {
		LocalDateTime now = LocalDateTime.now();
        String afterNow = now.minus(0, ChronoUnit.DAYS).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        File file = new File(toForderName);
        File[] files = file.listFiles();
        for(File f:files){					//遍历File[]数组
        	String name = f.getName().trim().replace(".zip", "");
			name = name.substring(name.length()-10);
			if(afterNow.compareTo(name)==1){//这就是可以删除的备份文件了
				f.delete();
			}
		}
        
	}
}