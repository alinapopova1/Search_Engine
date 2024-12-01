package searchengine.services.crawlingpages;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.ConnectionSettings;
import searchengine.model.PageEntity;

import java.io.IOException;
import java.util.concurrent.ConcurrentSkipListSet;

import static java.lang.Thread.sleep;

public class Parsing {
    private static ConcurrentSkipListSet<String> links = new ConcurrentSkipListSet<>();

    static boolean AllowedUrl(String link) {
        String regex = "http[s]?://[^#,\\s]*\\.?.*?\\.ru[^#,\\s]*";
        return link.matches(regex);
    }

    static boolean allowedExtension(String link) {
        link.toLowerCase();
        return  link.contains(".jpg") || link.contains(".pdf") || link.contains(".zip")
                || link.contains(".doc") || link.contains(".xlsx") || link.contains(".docx")
                || link.contains(".sql");
    }

    public static ConcurrentSkipListSet<String> getLinks(String url, PageEntity pageEntity, ConnectionSettings connectionSettings) {
        ConcurrentSkipListSet<String> links = new ConcurrentSkipListSet<>();
        try {
            sleep(500);
            Document document = Jsoup.connect(url).userAgent(connectionSettings.getUserAgent()).referrer(connectionSettings.getReferer())
                    .timeout(10000).get();
            Elements elements = document.select("body").select("a");

            for (Element element : elements) {
                String attribute = element.attribute("href").getValue();
                String link = element.absUrl("href");

                if (!allowedExtension(link) && !attribute.startsWith("http") && !attribute.equals("/")                ) {
                    links.add(link);
                }
            }
            pageEntity.setCode(document.connection().response().statusCode());
            pageEntity.setContent(document.head()+String.valueOf(document.body()));
        } catch (InterruptedException | IOException e) {
            pageEntity.setCode(getStatusCode(e));
            pageEntity.setContent(e.toString());
        }
        return links;
    }

    public static ConcurrentSkipListSet<PageEntity> getLinksNew(String url, LinkTreeNew linkTreeNew, ConnectionSettings connectionSettings) {
        ConcurrentSkipListSet<PageEntity> links = new ConcurrentSkipListSet<>();
        PageEntity pageEntity = new PageEntity();
        try {
            sleep(500);
            Document document = Jsoup.connect(url).userAgent(connectionSettings.getUserAgent()).referrer(connectionSettings.getReferer())
                    .timeout(10000).get();
            Elements elements = document.select("body").select("a");

            for (Element element : elements) {
                String link = element.attr("href");

                if (!allowedExtension(link) && !AllowedUrl(link)  && !link.equals("/")
                ) {
                    pageEntity.setPath(link);
                    pageEntity.setCode(document.connection().response().statusCode());
                    pageEntity.setContent(document.head()+String.valueOf(document.body()));
                    links.add(pageEntity);
                    linkTreeNew.addLink(link);
                }
            }

        } catch (InterruptedException | IOException e) {
            pageEntity.setCode(getStatusCode(e));
            pageEntity.setContent(e.toString());
        }
        return links;
    }

    public static int getStatusCode(Exception exception){
        String message = exception.toString();
        int statusCode;
        if (message.contains("Status=401")){
            statusCode = 401;
        } else if (message.contains("Status=403")) {
            statusCode = 403;
        } else if (message.contains("Status=404")) {
            statusCode = 404;
        }else if (message.contains("Status=500")) {
            statusCode = 500;
        }else if (message.contains("Status=503")) {
            statusCode = 503;
        } else if (message.contains("UnsupportedMimeTypeException")) {
            statusCode = 415;
        } else if (message.contains("UnknownHostException")) {
            statusCode = 401;
        } else if (message.contains("ConnectException: Connection refused")) {
            statusCode = 500;
        }else if (message.contains("SSLHandshakeException")) {
            statusCode = 525;
        }else {
            statusCode=-1;
        }
        return statusCode;
    }
}
