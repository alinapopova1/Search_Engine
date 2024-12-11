package searchengine.services.crawlingpages;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class LinkTree {
    private final String url;
    CopyOnWriteArrayList<LinkTree> linkChildren  = new CopyOnWriteArrayList<>();

    public LinkTree(String url) {
        this.url = url;
        linkChildren = new CopyOnWriteArrayList<>();
    }

    public void addLink(LinkTree link) {
        linkChildren.add(link);
    }

    private void recursiveCrawling(List<String> result){
        for (LinkTree linkTree1: getLinkChildren()){
            result.add(linkTree1.url);
            linkTree1.recursiveCrawling(result);
        }
    }
}
