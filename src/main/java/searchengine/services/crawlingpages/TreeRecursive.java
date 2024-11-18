package searchengine.services.crawlingpages;

import lombok.extern.slf4j.Slf4j;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TreeRecursive extends RecursiveTask<LinkTree> {
    private SiteEntity site;
    private LinkTree linkTree;
    //    private static CopyOnWriteArrayList<String> visitLinks = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<String, String> visitedPages;
    private SiteRepositories siteRepositories;
    private PageRepositories pageRepositories;
    private AtomicBoolean statusIndexingProcess;

    public TreeRecursive(SiteEntity site, LinkTree linkTree, ConcurrentHashMap<String, String> visitedPages, SiteRepositories siteRepositories, PageRepositories pageRepositories, AtomicBoolean statusIndexingProcess) {
        this.site = site;
        this.linkTree = linkTree;
        this.visitedPages = visitedPages;
        this.siteRepositories = siteRepositories;
        this.pageRepositories = pageRepositories;
        this.statusIndexingProcess = statusIndexingProcess;
    }

    @Override
    protected LinkTree compute() {
        log.info("TreeRecursive.compute -> start");
        if (!statusIndexingProcess.get()) {
            log.warn("TreeRecursive.compute -> Indexing stopped by user: " + linkTree.getUrl());
            return linkTree;
        } else if (visitedPages.get(linkTree.getUrl()) != null) {
            log.info("TreeRecursive.compute -> Already visited url: " + linkTree.getUrl());
            return linkTree;
        }
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(linkTree.getUrl().replace(site.getUrl(), ""));
        pageEntity.setSite(site);

//        visitLinks.add(linkTree.getUrl());

        ConcurrentSkipListSet<String> links = Parsing.getLinks(linkTree.getUrl(), pageEntity);

        for (String link : links) {
            if (!visitedPages.containsValue(link)) {
//                pageEntity.setSite(site);
//                pageEntity.setPath(link);
//                pageEntity.setCode();
//                pageEntity.setContent();
                visitedPages.put(linkTree.getUrl(), link);
                linkTree.addLink(new LinkTree(link));
            }
        }

        if (!pageEntity.getPath().equals(pageEntity.getSite().getUrl())){
            pageRepositories.save(pageEntity);
            site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
            siteRepositories.save(site);
        }

        List<TreeRecursive> newTask = new ArrayList<>();
        for (LinkTree l : linkTree.getLinkChildren()) {
            TreeRecursive rec = new TreeRecursive(site, l, visitedPages, siteRepositories, pageRepositories, statusIndexingProcess);
            rec.fork();
            newTask.add(rec);
        }
        for (TreeRecursive task : newTask) {
            task.join();
        }
        return linkTree;
    }
}
