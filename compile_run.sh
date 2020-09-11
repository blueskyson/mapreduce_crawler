javac -cp .:./MRcrawler:$(hadoop classpath) MRcrawler.java
mv *.class MRcrawler
jar -cf MRcrawler.jar -C MRcrawler .
hadoop jar MRcrawler.jar MRcrawler base_urls.txt 2
