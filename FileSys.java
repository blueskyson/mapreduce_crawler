import java.io.IOException;
import java.io.File;
import java.net.URI;
import java.util.Scanner;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.LocatedFileStatus;
public class FileSys {
    public static boolean mkdir(String dir_str) {
        File dir = new File(dir_str);
        if (!dir.exists()) {
            if (dir.mkdir()) {
                //System.out.println("Create directory: " + dir_str);
            } else {
                System.out.println("Failed to create directory: " + dir_str);
                return false;
            }
        } else {
            //System.out.println("\"" + dir_str + "\" already exists");
        }
        return true;
    }

    public static boolean fs_mkdir(String dir_str) {
        Configuration conf = new Configuration();
        conf.addResource("core-site.xml");
        try {
            FileSystem fs = FileSystem.get(URI.create("/"), conf);
            if (fs.exists(new Path(dir_str))) {
                //System.out.println("Directory: \"" + dir_str + "\" already exists in HDFS.");
                return true;
            }
            if (fs.mkdirs(new Path(dir_str))) {
                //System.out.println("Create directory: " + dir_str + " in HDFS.\n");
                return true;
            } else {
                System.out.println("    Failed to create directory: " + dir_str + " in HDFS.");
                return false;
            }
        } catch (IOException e) {
            System.out.println("    IOException occurred when creating " + dir_str + " in HDFS");
            //e.printStackTrace();
            return false;
        }
    }

    public static boolean fs_upload(String localDir, String hdfsDir) {
        Configuration conf = new Configuration();
        conf.addResource("core-site.xml");
        try {
            FileSystem fs = FileSystem.get(URI.create("/"), conf);
            fs.copyFromLocalFile(new Path(localDir), new Path(hdfsDir));
            //System.out.println("Copy " + localDir + " to HDFS successfully");
        } catch (IOException e) { 
            System.out.println("    IOException occurred when copying " + localDir + " to HDFS");
            //e.printStackTrace();
            return false;
        }
        return true;
    }

    public static int fs_save(String str, String hdfsDir) {
        Configuration conf = new Configuration();
        conf.addResource("core-site.xml");
        try {
            FileSystem fs = FileSystem.get(URI.create("/"), conf);
            Path hdfswritepath = new Path(hdfsDir);
            if (fs.exists(hdfswritepath)) {
                //System.out.println("Directory: \"" + dir_str + "\" already exists in HDFS.");
                return 1;
            }
            FSDataOutputStream outputStream = fs.create(hdfswritepath);
            outputStream.writeBytes(str);
            outputStream.close();
            return 2;
        } catch (IOException e) {
            return 0;
        }
    }

    public static int[] fs_read_crawler_info(String hdfsDir) {
        Configuration conf = new Configuration();
        conf.addResource("core-site.xml");
        int[] result = new int[2];
        try {
            FileSystem fs = FileSystem.get(URI.create("/"), conf);
            Path inDir = new Path(hdfsDir);
            if (!fs.exists(inDir)) {
                return null;
            }
            RemoteIterator<LocatedFileStatus> fileListItr = fs.listFiles(inDir, false);
            while (fileListItr != null && fileListItr.hasNext()) {
                Path inFile = fileListItr.next().getPath();
                FSDataInputStream in = fs.open(inFile);
                Scanner s = new Scanner(in);
                result[0] = 0;
                result[1] = 0;
                while(s.hasNext()) {
                    result[0]++;
                    result[1] += Integer.valueOf(s.next());
                }
            }
            return result;
        } catch (IOException e) {
            return null;
        }
    }
}
