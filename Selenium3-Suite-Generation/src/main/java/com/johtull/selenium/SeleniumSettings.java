package com.johtull.selenium;

import com.beust.jcommander.Parameter;

public class SeleniumSettings {
	@Parameter(names="-file", description="producting mapping XML")
	public String myFileName;
	
	@Parameter(names="-browser", description="test browser. options: gecko, chrome")
	public String browser;
	
	@Parameter(names="-binary", description="location of firefox.exe - not used for chrome")
	public String binary = "";
	
	@Parameter(names="-env", description="environment/subdomain")
	public String env = "";
	
	@Parameter(names="-site", description="site to be tested")
	public String siteUrl;
	
	@Parameter(names="-order", description="test cart functionality", arity = 1)
	public boolean isDoorOrder = false;
	
	@Parameter(names="-log", description="log file name")
	public String logFile = "results.txt";
	
	@Parameter(names="-debug", description="set debug level. 1 = include image mismatch")
	public int debugLevel = 0;
}