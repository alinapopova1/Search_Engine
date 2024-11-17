package searchengine.services.crawlingpages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveTask;

public class TreeRecursive extends RecursiveTask<LinkTree> {
    private LinkTree linkTree;
    private static CopyOnWriteArrayList<String> visitLinks = new CopyOnWriteArrayList<>();

    public TreeRecursive(LinkTree linkTree) {
        this.linkTree = linkTree;
    }

    @Override
    protected LinkTree compute() {
        visitLinks.add(linkTree.getUrl());

        ConcurrentSkipListSet<String> links = Parsing.getLinks(linkTree.getUrl());
        for (String link : links) {
            if (!visitLinks.contains(link)) {
                visitLinks.add(link);
                linkTree.addLink(new LinkTree(link));
            }
        }
        List<TreeRecursive> newTask = new ArrayList<>();
        for (LinkTree l : linkTree.getLinkChildren()) {
            TreeRecursive rec = new TreeRecursive(l);
            rec.fork();
            newTask.add(rec);
        }
        for (TreeRecursive task : newTask) {
            task.join();
        }
return linkTree;
    }
}
