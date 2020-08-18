package PKI.Tool.job;

import java.io.IOException;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务
 * @author wangjiping
 *
 */
@Component
public class TimedTask {
	public static String formForderName = "";
	public static String ToForderName = "";
	/**
	 * 将文件夹formForderName压缩放到新的文件夹ToForderName
	 */
	@Scheduled(cron="0 0 2 * * ?")
	public void doSbdfForder(){
		try {
			FileToZip.ZipFolderMethod(formForderName, ToForderName);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * 每天凌晨2点清理一次30天之前的压缩包
	 */
	@Scheduled(cron="0 30 2 * * ?")
	public void clearZipForder(){
		FileToZip.ClearForder(ToForderName);
	}
}
