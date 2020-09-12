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
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;

public class MRcrawler {
    public static String fs_root_dir, url_dir, fs_url_dir;
    public static String level_str, next_lv_dir, fs_lv_dir;
    public static int crawl_num, web_num, web_abort, web_recrawled, max_level;

    public static void main(String args[]) throws IOException {
        Validate.isTrue(args.length == 2, "usage: hadoop jar MRcrawler.jar MRcrawler [base urls file] [crawl depth]");

        fs_root_dir = "/MRcrawler";
        if (!FileSys.fs_mkdir(fs_root_dir)) {
            System.out.println("Unable to create directory in HDFS.");
            return;
        }
        
        if (!FileSys.fs_mkdir(fs_root_dir + "/level0")) {
            System.out.println("Unable to create directory in HDFS.");
            return;
        }
        
        if (!FileSys.fs_mkdir(fs_root_dir + "/level0/Urls")) {
            System.out.println("Unable to create directory in HDFS.");
            return;
        }

        
        Configuration conf = new Configuration();
        crawl_num = 0;
        web_num = 0;
        web_abort = 0;
        web_recrawled = 0;
        max_level = Integer.valueOf(args[1]);
        if (!FileSys.fs_upload(args[0], fs_root_dir + "/level0/Urls")) {
            System.out.println("Unable to upload base urls to HDFS");
            return;
        }
        System.out.println("\nPress any key to start");
        System.in.read();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        long job1_start_t = System.currentTimeMillis();
        String job1_start_str = formatter.format(new Date(job1_start_t));
        System.out.println("*** Start to crawl. Current time: " + job1_start_str + " ***\n");
        
        try {
            for (int level = 1; level <= max_level; level++) {
                String now_dir = fs_root_dir + "/level" + level;
                Job job1 = Job.getInstance(conf, "crawl");
                job1.setJarByClass(MRcrawler.class);
                job1.setMapperClass(CrawlMapper.class);
                job1.setCombinerClass(SaveReducer.class);
                job1.setReducerClass(SaveReducer.class);
                job1.setOutputKeyClass(Text.class);
                job1.setOutputValueClass(TextPair.class);
                FileInputFormat.addInputPath(job1, new Path(fs_root_dir + "/level" + (level - 1) + "/Urls"));
                FileOutputFormat.setOutputPath(job1, new Path(now_dir));
                MultipleOutputs.addNamedOutput(job1, "Texts", TextOutputFormat.class, Text.class, Text.class);
                MultipleOutputs.addNamedOutput(job1, "Urls", TextOutputFormat.class, Text.class, Text.class);
                MultipleOutputs.addNamedOutput(job1, "RecrawlTime", TextOutputFormat.class, Text.class, IntWritable.class);
                job1.waitForCompletion(true);
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

        long job2_start_t = System.currentTimeMillis();
        String job2_start_str = formatter.format(new Date(job2_start_t));
        System.out.println("*** Start to count words. Current time: " + job2_start_str + " ***\n");

        try {
            Job job2 = Job.getInstance(conf, "word count");
            job2.setJarByClass(MRcrawler.class);
            job2.setMapperClass(TokenizerMapper.class);
            job2.setCombinerClass(IntSumReducer.class);
            job2.setReducerClass(IntSumReducer.class);
            job2.setOutputKeyClass(Text.class);
            job2.setOutputValueClass(IntWritable.class);
            for (int i = 1; i <= max_level; i++)
                MultipleInputs.addInputPath(job2, new Path(fs_root_dir + "/level" + i + "/Texts"),
                        TextInputFormat.class, TokenizerMapper.class);
            FileOutputFormat.setOutputPath(job2, new Path(fs_root_dir + "/wordcount"));
            job2.waitForCompletion(true);
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
        
        long job2_finish_t = System.currentTimeMillis();
        long job2_cost_sec = (job2_finish_t - job2_start_t) / 1000;
        String job2_finish_str = formatter.format(new Date(job2_finish_t));
        System.out.println("\n*** Finish counting. Current time: " + job2_finish_str + " ***");
        System.out.println("\n*** Crawl cost time: " + (int)(job1_cost_sec / 60) + "m " + (int)(job1_cost_sec % 60) + "s ***");
        System.out.println("\n*** Count cost time: " + (int)(job2_cost_sec / 60) + "m " + (int)(job2_cost_sec % 60) + "s ***\n");
        
        int crawlnum = 0, recrawlnum = 0, recognizenum = 0;
        for (int i = 1; i <= max_level; i++) {
            int[] result = FileSys.fs_read_crawler_info("/MRcrawler/level" + i + "/RecrawlTime/");
            System.out.println("\n*** In level1: crawl = " + (result[0] + result[1]) + " recognize = " + result[0] + " recrawl = " + result[1]);
            crawlnum += result[0] + result[1];
            recognizenum += result[1];
            recrawlnum += result[1];
        }
        System.out.println("\n*** Total: crawl = " + crawlnum + " recognize = " + recognizenum + " recrawl = " + recrawlnum);
    }
    
    public static class CrawlMapper extends Mapper<Object, Text, Text, TextPair> {
        private Text weburl = new Text();
        private Text webtext = new Text();
        public void map(Object key, Text value, Context context) {
            StringTokenizer itr = new StringTokenizer(value.toString(), "\n");
            UrlValidator urlCheck = new UrlValidator();
            
            while (itr.hasMoreTokens()) {
                String target_url = itr.nextToken();
                System.out.println("\n * " + target_url);
                if (!urlCheck.isValid(target_url)) {
                    continue;
                }
                
                Response res = WebCrawl.ConnectResponse(target_url);
                if (res == null) {
                    continue;
                }
                
                Document doc;
                try {
                    doc = res.parse();
                    weburl.set(target_url);
                    context.write(weburl, new TextPair(WebCrawl.getWords(doc), WebCrawl.getLinks(doc)));
                } catch (IOException e) {
                    //System.out.println("    IOException occurred when parsing " + target_url);
                    continue;
                } catch (InterruptedException e) {
                    continue;
                }
            }
        }
    }

    public static class SaveReducer extends Reducer<Text, TextPair, Text, Text> {
        private Text nulltext = new Text();
        private TextPair value;
        private MultipleOutputs<Text, Text> mos;
        private IntWritable recrawltime = new IntWritable();
        public void setup(Context context) throws IOException, InterruptedException {
            mos = new MultipleOutputs<Text, Text>(context);
            nulltext.set("");
        }

        public void reduce(Text key, Iterable<TextPair> values, Context context)
                throws IOException, InterruptedException {
            value = values.iterator().next();
            int recrawl = 0;
            for (TextPair val : values) {
                recrawl++;
            }
            recrawltime.set(recrawl);
            mos.write("Texts", nulltext, value.getFirst(), "Texts/text");
            mos.write("Urls", nulltext, value.getSecond(), "Urls/urls");
            mos.write("RecrawlTime", nulltext, recrawltime, "RecrawlTime/recrawltime");
        }
    }

    public static class TokenizerMapper
        extends Mapper<Object, Text, Text, IntWritable> {
        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();

        public void map(Object key, Text value, Context context
                    ) throws IOException, InterruptedException {
        StringTokenizer itr = new StringTokenizer(value.toString(), " \"\n\'.,+-*/%()[]{}<>`~!@#?");
            while (itr.hasMoreTokens()) {
                word.set(itr.nextToken());
            context.write(word, one);
            }
        }
    }

    public static class IntSumReducer
       extends Reducer<Text,IntWritable,Text,IntWritable> {
        private IntWritable result = new IntWritable();

        public void reduce(Text key, Iterable<IntWritable> values, 
                Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            result.set(sum);
            context.write(key, result);
        }
    }
}
