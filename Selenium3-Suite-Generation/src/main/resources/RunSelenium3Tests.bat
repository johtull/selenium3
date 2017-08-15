@echo off
setlocal EnableDelayedExpansion

:: SCRIPT VERSION 1.0
:: This script is compatible with Selenium 3.0.1

:: Selenium 3 JAR requires:
::
::	- JRE 1.8
::
::	- AND -
::
::	- Firefox 48+
::	- geckodriver.exe
::		- https://github.com/mozilla/geckodriver/releases
::		- v0.13.0 has a massive memory leak (win7x64)
::		- v0.11.1 works for everything except IM sample ordering
::
::	- OR -
::
::	- Chrome 55+
::	- chromedriver.exe
::		- https://sites.google.com/a/chromium.org/chromedriver
::		- v2.27 works as intended (Preferred test)

:: =========================================================================

:: set environment/subdomain
::	possible options:
::	dev, qa, stg, www
set env=www

:: for Firefox
::set browser=gecko
::set firefoxBinary=C:\Program Files (x86)\Mozilla Firefox\firefox.exe

:: for Chrome
set browser=chrome
set firefoxBinary=


:: To generate a runnable Selenium 3 WebDriver JAR:
:: Right click project -> Run As -> Run Configurations
:: From the left menu, click 'Maven Build' and click the New button above that menu
:: Set the base directory to the current workspace project
:: Set goals to "assembly:single"
:: Click Run
:: Upon successful creation, JAR will be saved to the 'target' folder for the project


:: This logic assumes your JAR is contains 'Selenium' in the name
set seleniumJar=
if exist "*Selenium*.jar" (
	echo Selenium JAR detected
	for /r %%i in (*Selenium*.jar) do set seleniumJar=^"%%i^"
	if exist "geckodriver.exe" (
		echo geckodriver.exe detected
	) else if exist "chromedriver.exe" (
		echo chromedriver.exe detected
	) else (
		echo No webdriver found. Please download one from the following sites:
		echo geckodriver download: https://github.com/mozilla/geckodriver/releases
		echo chromedriver download: https://sites.google.com/a/chromium.org/chromedriver/
		pause
		exit
	)
) else (
	echo No Selenium JAR found. Please add the JAR to this folder. Open this script in a text editor for more information.
	pause
	exit
)
echo -----------------------------------------

:: run product tests
java -jar %seleniumJar% -browser %browser% -binary ^"%firefoxBinary%^" -env %env% -site example.com -file products_001.xml -order false -log example_products.txt

:: run door ordering tests
java -jar %seleniumJar% -browser %browser% -binary ^"%firefoxBinary%^" -env %env% -site example.com -file doors_001.xml -order true -log example_doors.txt

:: add as many executions as needed

echo -----------------------------------------
echo Please check the result and log files.
pause