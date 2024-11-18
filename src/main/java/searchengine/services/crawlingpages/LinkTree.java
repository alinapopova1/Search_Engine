package searchengine.services.crawlingpages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LinkTree {
    private String url;
    CopyOnWriteArrayList<LinkTree> linkChildren  = new CopyOnWriteArrayList<>();

    public LinkTree(String url) {
        this.url = url;
        linkChildren = new CopyOnWriteArrayList<>();
    }

    public String getUrl() {
        return url;
    }

    public void addLink(LinkTree link) {
        linkChildren.add(link);
    }

    public CopyOnWriteArrayList<LinkTree> getLinkChildren() {
        return linkChildren;
    }

    public List<String> getAllChildrenLink(){
        List<String> result = new ArrayList<>();
        this.recursiveCrawling(result);
        return result;
    }

    private void recursiveCrawling(List<String> result){
        for (LinkTree linkTree1: getLinkChildren()){
            result.add(linkTree1.url);
            linkTree1.recursiveCrawling(result);
        }
    }
}
