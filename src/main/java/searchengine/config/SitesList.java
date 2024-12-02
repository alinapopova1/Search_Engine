package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesList {
    private List<Site> sites;

    public List<String> getUrls(){
        List<String> urls = new ArrayList<>();
        for (Site site : sites){
            urls.add(site.getUrl());
        }
        return urls;
    }

    public Site getSiteByUrl(String url){
        for (Site site: getSites()){
            if (site.getUrl().equals(url)){
                return site;
            }
        }
        return null;
    }
}
