@echo off
 
rem 两个变量拼接，等号前后一定不要有空格
set now=%date:~0,4%-%date:~5,2%-%date:~8,2%
set basePath=C:\Users\wangj01052\Desktop\tools\docx
set sourcePath=%basePath%\%now%
echo java -DsourcePath=%sourcePath% -jar StructDocxToDB.jar

pause