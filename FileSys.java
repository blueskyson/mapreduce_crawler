import java.io.IOException;
import java.io.File;
import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class FileSys {
    public static boolean mkdir(String dir_str) {
        File dir = new File(dir_str);
        if (!dir.exists()) {
            if (dir.mkdir()) {
                System.out.println("Create directory: " + dir_str);
            } else {
                System.out.println("Failed to create directory: " + dir_str);
                return false;
            }
        } else {
            System.out.println("\"" + dir_str + "\" already exists");
        }
        return true;
    }

    public static boolean fs_mkdir(String dir_str) {
        Configuration conf = new Configuration();
        conf.addResource("core-site.xml");
        try {
            FileSystem fs = FileSystem.get(URI.create("/"), conf);
            if (fs.exists(new Path(dir_str))) {
                System.out.println("Directory: \"" + dir_str + "\" already exists in HDFS.");
                return true;
            }
            if (fs.mkdirs(new Path(dir_str))) {
                System.out.println("Create directory: " + dir_str + " in HDFS.\n");
                return true;
            } else {
                System.out.println("Failed to create directory: " + dir_str + " in HDFS.\n");
                return false;
            }
        } catch (IOException e) {
            System.out.println("IOException occurred when creating directory in HDFS");
            e.printStackTrace();
            return false;
        }
    }

    public static boolean fs_upload(String localDir, String hdfsDir) {
        Configuration conf = new Configuration();
        conf.addResource("core-site.xml");
        try {
            FileSystem fs = FileSystem.get(URI.create("/"), conf);
            fs.copyFromLocalFile(new Path(localDir), new Path(hdfsDir));
            System.out.println("Copy " + localDir + " to HDFS successfully\n");
        } catch (IOException e) { 
            System.out.println("IOException occurred when copying " + localDir + " to HDFS\n");
            e.printStackTrace();
            return false;
        }
        return true;
    }   
}
