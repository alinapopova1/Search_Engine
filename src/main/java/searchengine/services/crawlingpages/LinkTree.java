package searchengine.services.crawlingpages;

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

}
