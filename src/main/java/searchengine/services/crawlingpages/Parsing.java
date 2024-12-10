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

    public static boolean AllowedUrl(String link) {
        String regex = "http[s]?://[^#,\\s]*\\.?.*?\\.ru[^#,\\s]*";
        return link.matches(regex);
    }

    /**
     *
     * @param link вложенная страница
     * @return является ли она урлом
     */
    static boolean allowedExtension(String link) {
        link.toLowerCase();
        return link.contains(".jpg") || link.contains(".pdf") || link.contains(".zip")
                || link.contains(".doc") || link.contains(".xlsx") || link.contains(".docx")
                || link.contains(".sql");
    }

    /**
     * Выполняет запрос к странице, получает список урлов, сохраняет результат запроса в PageEntity
     * @param url урл по которому выполняем запрос
     * @param pageEntity объект страницы в БД, куда сохраняем результат выполнения запроса
     * @param connectionSettings параметры соеденения из конфига
     * @return список полученных урлов со страницы
     */
    public static ConcurrentSkipListSet<String> getLinks(String url, PageEntity pageEntity, ConnectionSettings connectionSettings) {
        ConcurrentSkipListSet<String> links = new ConcurrentSkipListSet<>();
        try {
            sleep(500);
            Document document = getJsoupDocument(url, connectionSettings);
            Elements elements = document.select("body").select("a");

            for (Element element : elements) {
                String attribute = element.attribute("href").getValue();
                String link = element.absUrl("href");

                if (attribute.matches("/.+") && element.hasAttr("href")&& !allowedExtension(link)) {
                    if (link.endsWith("/")) {
                        links.add(link);
                    } else {
                        links.add(link + "/");
                    }
                }
            }
            pageEntity.setCode(document.connection().response().statusCode());
            pageEntity.setContent(document.head() + String.valueOf(document.body()));
        } catch (InterruptedException | IOException e) {
            pageEntity.setCode(getStatusCode(e));
            pageEntity.setContent(e.toString());
        }
        return links;
    }

    public static Document getJsoupDocument(String url, ConnectionSettings connectionSettings) throws IOException {
        return Jsoup.connect(url).userAgent(connectionSettings.getUserAgent()).referrer(connectionSettings.getReferer())
                .timeout(10000).get();
    }

    /**
     * Конвертирует полученный ответ в понятый http status code
     * @param exception
     * @return http status code
     */
    public static int getStatusCode(Exception exception) {
        String message = exception.toString();
        int statusCode;
        if (message.contains("Status=401")) {
            statusCode = 401;
        } else if (message.contains("Status=403")) {
            statusCode = 403;
        } else if (message.contains("Status=404")) {
            statusCode = 404;
        } else if (message.contains("Status=500")) {
            statusCode = 500;
        } else if (message.contains("Status=503")) {
            statusCode = 503;
        } else if (message.contains("UnsupportedMimeTypeException")) {
            statusCode = 415;
        } else if (message.contains("UnknownHostException")) {
            statusCode = 401;
        } else if (message.contains("ConnectException: Connection refused")) {
            statusCode = 500;
        } else if (message.contains("SSLHandshakeException")) {
            statusCode = 525;
        } else {
            statusCode = -1;
        }
        return statusCode;
    }
}
