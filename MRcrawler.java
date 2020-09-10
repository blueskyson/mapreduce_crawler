import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.IOException;
import java.io.File;
import java.util.StringTokenizer;

import org.jsoup.helper.Validate;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;

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
    public static String fs_base;
    public static int crawl_num, web_num, web_abort, web_recrawled, max_level;

    public static void main(String args[]) throws IOException {
        Validate.isTrue(args.length == 2, "usage: hadoop jar MRcrawler.jar MRcrawler [base urls file] [crawl depth]");
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
        crawl_num = 0;
        web_num = 0;
        web_abort = 0;
        web_recrawled = 0;
        max_level = Integer.valueOf(args[1]);
        if (!FileSys.fs_upload(args[0], fs_base)) {
            System.out.println("Unable to upload base urls to HDFS");
            return;
        }
        System.out.println("\nPress any key to start");
        System.in.read();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        long job1_start_t = System.currentTimeMillis();
        String job1_start_str = formatter.format(new Date(job1_start_t));
        System.out.println("\n*** Start to crawl. Current time: " + job1_start_str + " ***\n");
        
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
                job1.setJarByClass(MRcrawler.class);
                job1.setMapperClass(CrawlMapper.class);
                job1.setNumReduceTasks(0);
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

        long job1_finish_t = System.currentTimeMillis();
        long job1_cost_sec = (job1_finish_t - job1_start_t) / 1000;
        String job1_finish_str = formatter.format(new Date(job1_finish_t));
        System.out.println("\n*** Finish crawling. Current time: " + job1_finish_str + " ***");
        System.out.println("\n*** crawl_num: " + crawl_num + " web_num: " + web_num + " ***");
        System.out.println("\n*** web_abort: " + web_abort + " web_recrawled: " + web_recrawled + " ***");
        System.out.println("\n*** cost time: " + (int)(job1_cost_sec / 60) + "m " + (int)(job1_cost_sec % 60) + "s ***");

    }

    public static class CrawlMapper extends Mapper<Object, Text, Text, IntWritable> {
        public void map(Object key, Text value, Context context) {
            StringTokenizer itr = new StringTokenizer(value.toString(), "\n");
            UrlValidator urlCheck = new UrlValidator();
            while (itr.hasMoreTokens()) {
                String target_url = itr.nextToken();
                if (!urlCheck.isValid(target_url)) {
                    System.out.println("    Invalid URL: " + target_url);
                    continue;
                }
                Response res = WebCrawl.ConnectResponse(target_url);
                if (res == null) {
                    System.out.println("    Failed to crawl " + target_url);
                    web_abort++;
                    continue;
                } else {
                    crawl_num++;
                }
                Document doc;
                try {
                    doc = res.parse();
                } catch (IOException e) {
                    System.out.println("    IOException occurred when parsing " + target_url);
                    //e.printStackTrace();
                    web_abort++;
                    continue;
                }
                
                String fileName = doc.title().replace(' ', '_').replace(',', '_').replace(':', '_');
                String fileDir = root_dir + "/" + fileName + "-.txt";
                int save_value = WebCrawl.saveText(WebCrawl.getWords(doc), fileDir);
                if (save_value == 1) {
                    //System.out.println("\nAdd file " + fileDir + " to MapReduce input path.\n");
                    if (FileSys.fs_upload(fileDir, fs_root_dir)) {
                        String hdfsFileDir = fs_root_dir + "/" + fileName + "-.txt";
                        web_num++;
                    } else {
                        web_abort++;
                        continue;
                    }
                } else if (save_value == 2){
                    web_recrawled++;
                    web_abort++;
                    continue;
                } else {
                    web_abort++;
                    continue;
                }
                
                String urlsDir = next_lv_dir + "/" + fileName + "-.txt";
                save_value = WebCrawl.saveText(WebCrawl.getLinks(doc), urlsDir);
                if (save_value == 1) {
                    //System.out.println("\nAdd file " + urlsDir + " to MapReduce url path.\n");
                    FileSys.fs_upload(urlsDir, fs_lv_dir);
                }
            }
        }
    }
}
