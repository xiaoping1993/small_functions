@echo off
 
rem ��������ƴ�ӣ��Ⱥ�ǰ��һ����Ҫ�пո�
set now=%date:~0,4%-%date:~5,2%-%date:~8,2%
set basePath=C:\Users\wangj01052\Desktop\tools\docx
set sourcePath=%basePath%\%now%
echo java -DsourcePath=%sourcePath% -jar StructDocxToDB.jar

pause