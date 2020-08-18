package PKI.Tool;

import java.util.Scanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import PKI.Tool.job.TimedTask;

@SpringBootApplication
@EnableScheduling
public class Application {
	public static void main(String[] args) {
		getParams();
		SpringApplication.run(Application.class, args);
	}
	private static void getParams(){
		@SuppressWarnings("resource")
		Scanner sc = new Scanner(System.in);  
		System.out.println("请输入想要备份的文件夹绝对路径：");
		TimedTask.formForderName  = sc.next();
		System.out.println("请输入备份到的目标件夹绝对路径：");
		TimedTask.ToForderName = sc.next();
	}

}
