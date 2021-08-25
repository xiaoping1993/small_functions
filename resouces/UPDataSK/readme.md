## 工具适用方法
### 准备环境：
	前置机java环境（已完成）
	c:/java/ UPLoadSKJson目录下（已完成）
	放如下文件：
		UPDataSK.jar：申康数据自动上传工具
		D46/param.properties   D46专病库参数
		D46/run.dat       D46数据上传批处理文件，后面通过window计划任务定时执行
		M15/ param.properties M15专病库参数
		M15/run.dat      M15数据上传批处理文件，后面通过window计划任务定时执行
	Sqlserver数据库各个专病库数据准备好，切记要与你上传的模板文件一致
### 实施操作：
	前提：前置机上的sqlserver数据准备好
	目的：上传D46数据
		1）	确定好模板文件、值域文件，上传
		2）	根据模板文件修改param.properties文件（附件上有备注，或者直接丢给我来修改）
		3）	先本地测试下，直接点击对应的run.bat，查看命令窗口无异常、同目录下生成的最新日志文件显示上传成功（日志中显示你上传成功的是主表timestamp哪些区间的数据）
		4）	配置window计划任务执行run.bat操作，计划频率看你每次上传的数据量自行配置，建议1小时执行一次，如果每次上传的患者10条的话
	目前：上传M15数据
		同上
		善后的工作：
			1）	定时自动我们这边专病库数据到前置机
			如果张景那边spotfire数据可以回传到前置机sqlserver最好
			不可以的话，我这边会再开发一个自动上传spotfire的导出的csv数据到前置机sqlserver上
			2）	上传具体专病库数据
			待我们这边所有专病库数据准备好、模板文件、值域文件确定
			需安排人员配置此专病库的计划任务自动上传对应json数据，具体方法参考上面
							善后的工作需要再安排个人来做，我会配合他
					