package searchengine.services.crawlingpages;

import lombok.extern.slf4j.Slf4j;
import searchengine.config.ConnectionSettings;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepositories;
import searchengine.repositories.LemmaRepositories;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;
import searchengine.services.LemmaFinder;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private LemmaRepositories lemmaRepositories;
    private IndexRepositories indexRepositories;
    private AtomicBoolean statusIndexingProcess;

    private final ConnectionSettings connectionSettings;

    public TreeRecursive(SiteEntity site, LinkTree linkTree, ConcurrentHashMap<String, String> visitedPages, SiteRepositories siteRepositories, PageRepositories pageRepositories, AtomicBoolean statusIndexingProcess, ConnectionSettings connectionSettings, LemmaRepositories lemmaRepositories, IndexRepositories indexRepositories) {
        this.site = site;
        this.linkTree = linkTree;
        this.visitedPages = visitedPages;
        this.siteRepositories = siteRepositories;
        this.pageRepositories = pageRepositories;
        this.lemmaRepositories = lemmaRepositories;
        this.indexRepositories = indexRepositories;
        this.statusIndexingProcess = statusIndexingProcess;
        this.connectionSettings = connectionSettings;
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
        pageEntity.setPath(linkTree.getUrl());
        pageEntity.setSite(site);

//        visitLinks.add(linkTree.getUrl());

        ConcurrentSkipListSet<String> links = Parsing.getLinks(linkTree.getUrl(), pageEntity, connectionSettings);

        for (String link : links) {
//            if(link.contains(site.getUrl())) {
                if (!visitedPages.containsValue(link)) {
//                pageEntity.setSite(site);
//                pageEntity.setPath(link);
//                pageEntity.setCode();
//                pageEntity.setContent();
                    visitedPages.put(linkTree.getUrl(), link);
                    linkTree.addLink(new LinkTree(link));
                }
//            }
        }

        if (!statusIndexingProcess.get()){
            throw new RuntimeException("Indexing stopped by user");
        }

        if (!pageEntity.getPath().equals(pageEntity.getSite().getUrl())){
            pageEntity = pageRepositories.save(pageEntity);
            try {
                saveLemmaAndIndexEntity(pageEntity);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
            siteRepositories.save(site);
        }

        List<TreeRecursive> newTask = new ArrayList<>();
        for (LinkTree l : linkTree.getLinkChildren()) {
            TreeRecursive rec = new TreeRecursive(site, l, visitedPages, siteRepositories, pageRepositories, statusIndexingProcess, connectionSettings, lemmaRepositories, indexRepositories);
            rec.fork();
            newTask.add(rec);
        }
        for (TreeRecursive task : newTask) {
            task.join();
        }
        return linkTree;
    }

    private void saveLemmaAndIndexEntity(PageEntity pageEntity) throws IOException {
        LemmaFinder lemmaFinderRus = LemmaFinder.getRusInstance();
//        LemmaFinder lemmaFinderEng = LemmaFinder.getEngInstance();
        if (pageEntity.getCode() == 200){
            Map<String, Integer> lemmaCollect = lemmaFinderRus.collectLemmas(pageEntity.getContent());
            Set<String> lemmas = lemmaFinderRus.getLemmaSet(pageEntity.getContent());

            IndexEntity indexEntity = new IndexEntity();

            for (String lemma: lemmas){
                LemmaEntity lemmaEntity = lemmaRepositories.findBySiteIdAndLemma(pageEntity.getSite().getId(), lemma);
                lemmaEntity.setId(pageEntity.getSite().getId());
                lemmaEntity.setLemma(lemma);
                if (lemmaEntity == null){
                    lemmaEntity.setFrequency(1);
                } else {
                    lemmaEntity.setFrequency(lemmaEntity.getFrequency()+1);
                }
                lemmaRepositories.save(lemmaEntity);
                indexEntity.setPage(pageEntity);
                indexEntity.setLemma(lemmaEntity);
                indexEntity.setRank(lemmaCollect.get(lemma));
                indexRepositories.save(indexEntity);
            }

        }
    }
}
