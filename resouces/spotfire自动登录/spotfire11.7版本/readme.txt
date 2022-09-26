1：配置
	/node-proxy-master/server.js
	修改位置：app.use('/', createProxyMiddleware({
	    //这里修改为目标地址
	    target: 'http://47.103.64.41:90'
	配置启动端口
		app.listen(port);
2：启动
	在操作系统环境中安装node
	之后此文件夹根目录下执行
	node server.js
	命令窗口中显示端口84启动成功即可
3：使用方法
	http://localhost:84?username=spotfire&password=spotfire&libaryId=256600f1-f50a-4276-9848-0b415ecd1721
     其中几个参数要注意：
	username
	password
	libaryId：你登录成功后跳转的目标模块文件id
4：注意事项：
	此免密登录功能是在未登录或者说没有存储登录信息时候才会跳转成功?
	这个bug已经处理，通过添加清楚cookie的脚本实现