package searchengine.services.crawlingpages;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.concurrent.ConcurrentSkipListSet;

import static java.lang.Thread.sleep;

public class Parsing {
    private static ConcurrentSkipListSet<String> links = new ConcurrentSkipListSet<>();

    private static boolean AllowedUrl(String link) {
        String regex = "http[s]?://[^#,\\s]*\\.?sendel\\.ru[^#,\\s]*";
        return link.matches(regex);
    }

    private static boolean allowedExtension(String link) {
        link.toLowerCase();
        return  link.contains(".jpg") || link.contains(".pdf") || link.contains(".zip")
                || link.contains(".doc") || link.contains(".xlsx") || link.contains(".docx")
                || link.contains(".sql");
    }

    public static ConcurrentSkipListSet<String> getLinks(String url) {
        ConcurrentSkipListSet<String> links = new ConcurrentSkipListSet<>();
        try {
            sleep(150);
            Document document = Jsoup.connect(url).timeout(10000).get();
            Elements elements = document.select("body").select("a");

            for (Element element : elements) {
                String link = element.absUrl("href");

                if (!allowedExtension(link) && AllowedUrl(link) && link.endsWith("/")
                ) {
                    links.add(link);
                }
            }
        } catch (InterruptedException | IOException e) {
            System.out.println(e + " for" + url);
        }
        return links;
    }
}
