ConsoleDownloader
=================

Console utility for downloading files via HTTP-protocol

In order to build full jar containing all dependencies run "gradlew fatJar".


How to run from console: java -jar ConsoleDownloader-all.jar -n 5 -l 200k -o output_path -f input_file

Where n - number of concurrently downloading threads, l - download speed limit, o - output folder, f - file containing links to download in format:

	<HTTP-link><space><file name to save>

Example:

	http://example.com/archive.zip my_archive.zip
	
	http://example.com/image.jpg picture.jpg
	
	......


If you want to see more info during download process just change the level of logging from INFO to DEBUG in file ConsoleDownloader\src\main\resources\logback.xml
