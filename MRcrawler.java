import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.IOException;
import java.io.File;
import java.util.StringTokenizer;

import org.jsoup.helper.Validate;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.jsoup.select.Elements;

import org.apache.commons.validator.routines.UrlValidator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;

public class MRcrawler {
    public static String root_dir, fs_root_dir, url_dir, fs_url_dir;
    public static String level_str, next_lv_dir, fs_lv_dir;
    public static String base_urls, fs_base;
    public static int web_num, web_abort, max_level;

    public static void main(String args[]) {
        Validate.isTrue(args.length == 2, "usage: hadoop jar MRcrawler.jar MRcrawler [root url] [crawl depth]");
        root_dir = "crawled_texts";
        url_dir = "urls";
        if (!FileSys.mkdir(root_dir)) {
            System.out.println("Unable to create directory \"" + root_dir + "\".");
            return;
        }
        if (!FileSys.mkdir(url_dir)) {
            System.out.println("Unable to create directory \"" + url_dir + "\".");
            return;
        }

        fs_root_dir = '/' + root_dir;
        fs_url_dir = '/' + url_dir;
        if (!FileSys.fs_mkdir(fs_root_dir)) {
            System.out.println("Unable to create directory \"" + root_dir + "\" in HDFS.");
            return;
        }
        if (!FileSys.fs_mkdir(fs_url_dir)) {
            System.out.println("Unable to create directory \"" + url_dir + "\" in HDFS.");
            return;
        }
        
        fs_base = fs_url_dir + "/base";
        if (!FileSys.fs_mkdir(fs_base)) {
            System.out.println("Unable to create root directory \"" + url_dir + "\" in HDFS.");
            return;
        }

        Configuration conf = new Configuration();
        web_num = 0;
        web_abort = 0;
        max_level = Integer.valueOf(args[1]);
        long job1_start_t, job1_finish_t;
        System.out.println("\n*** Start to count crawling execution time ***\n");
        job1_start_t = System.currentTimeMillis();
        
        File f = new File(args[0]);
        base_urls = f.getName();        //a file saving base links
        if (!FileSys.fs_upload(base_urls, fs_base)) {
            System.out.println("Unable to upload base urls to HDFS");
            return;
        }

        try {
            for (int level = 1; level <= max_level; level++) {
                next_lv_dir = url_dir + "/level" + level;
                if (!FileSys.mkdir(next_lv_dir)) {
                    System.out.println("Unable to create directory \"" + next_lv_dir + "\".");
                    return;
                }
                fs_lv_dir = '/' + next_lv_dir;
                if (!FileSys.fs_mkdir(fs_lv_dir)) {
                    System.out.println("Unable to create directory \"" + fs_lv_dir + "\" in HDFS.");
                    return;
                }
                Job job1 = Job.getInstance(conf, "crawl");
                FileInputFormat.addInputPath(job1, new Path(fs_base));
                //FileOutputFormat.setOutputPath(job1, new Path("/Output_level" + level));
                job1.setJarByClass(MRcrawler.class);
                job1.setMapperClass(CrawlMapper.class);
                job1.setCombinerClass(CountReducer.class);
                job1.setReducerClass(CountReducer.class);
                job1.setOutputFormatClass(NullOutputFormat.class);
                
                if (job1.waitForCompletion(true)) {
                    System.out.println(level_str + " Succeed.");
                    fs_base = fs_lv_dir;
                } else {
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }
    }

    public static class CrawlMapper extends Mapper<Object, Text, Text, IntWritable> {
        public void map(Object key, Text value, Context context) {
            StringTokenizer itr = new StringTokenizer(value.toString(), "\n");
            UrlValidator urlCheck = new UrlValidator();
            while (itr.hasMoreTokens()) {
                String target_url = itr.nextToken();
                if (!urlCheck.isValid(target_url)) {
                    System.out.println("Invalid URL: " + target_url);
                    web_abort++;
                    continue;
                }
                Response res = WebCrawl.ConnectResponse(target_url);
                if (res == null) {
                    System.out.println("    Failed to crawl this site.");
                    continue;
                }
                Document doc;
                try {
                    doc = res.parse();
                } catch (IOException e) {
                    System.out.println("    IOException occurred when parsing this website\n");
                    e.printStackTrace();
                    web_abort++;
                    continue;
                }
                
                String fileName = doc.title().replace(' ', '_').replace(',', '_').replace(':', '_');
                String fileDir = root_dir + "/" + fileName + "-.txt";
                int save_value = WebCrawl.saveText(WebCrawl.getWords(doc), fileDir);
                if (save_value == 1) {
                    System.out.println("\nAdd file " + fileDir + " to MapReduce input path.\n");
                    if (FileSys.fs_upload(fileDir, fs_root_dir)) {
                        String hdfsFileDir = fs_root_dir + "/" + fileName + "-.txt";
                        //MultipleInputs.addInputPath(job1, new Path(hdfsFileDir), TextInputFormat.class, CrawlMapper.class);
                        web_num++;
                    } else {
                        web_abort++;
                        continue;
                    }
                } else {
                    web_abort++;
                    continue;
                }
                
                String urlsDir = next_lv_dir + "/" + fileName + "-.txt";
                save_value = WebCrawl.saveText(WebCrawl.getLinks(doc), urlsDir);
                if (save_value == 1) {
                    System.out.println("\nAdd file " + urlsDir + " to MapReduce url path.\n");
                    if (FileSys.fs_upload(urlsDir, fs_lv_dir)) {
                        web_num++;
                    } else
                        web_abort++;
                } else if (save_value == 0){
                    web_abort++;
                }

            }
        }
    }

    public static class CountReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        
    }
}
