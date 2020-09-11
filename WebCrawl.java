import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.Connection.Response;
import org.jsoup.HttpStatusException;
public class WebCrawl {
    public static Response ConnectResponse(String url) {
        try {
            return Jsoup.connect(url).userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Safari/537.36 RuxitSynthetic/1.0 v6297461322 t38550 ath9b965f92 altpub").timeout(5000).execute();
        } catch (HttpStatusException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    public static int saveText(String words, String file_dir) {
        if (words == "") {
            //System.out.println("    No content. Skip saving this site.");
            return 0;
        }
        File words_file = new File(file_dir);
        try {
            if (!words_file.exists()) {
                if (!words_file.createNewFile()) {
                    System.out.println("    Failed to save file as " + file_dir);
                    return 0;
                }
                Files.write(Paths.get(file_dir), words.getBytes(StandardCharsets.UTF_8));
                //System.out.println("Save file as " + file_dir + " successfully");
                return 1;
            } else {
                //System.out.println("    This site has been crawled");
                return 2;
            }
        } catch (IOException e) {
            System.out.println("    IOException occurred when saving " + file_dir);
            //e.printStackTrace();
            return 0;
        }
    }
    
    public static String getWords(Document doc) {
        if (doc == null)
            return "";
        try {
            Elements elements = doc.body().select("*");
            String words = ""; 
            for (Element ele : elements) {
                if (!ele.ownText().equals(""))
                    words += ' ' + ele.ownText();
            }
            return words;
        } catch (NullPointerException e) {
            return "";
        }
    }

    public static String getLinks(Document doc) {
        if (doc == null)
            return "";
        Elements links = doc.select("a[href]");
        String link_words = "";
        for (Element link : links) {
            link_words += link.attr("abs:href") + '\n';
        }
        return link_words;
    }

}
