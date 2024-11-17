package searchengine.services.crawlingpages;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ForkJoinPool;

public class Main_Fork {

    public static void main(String[] args) {
        String url = "https://sendel.ru/";
        String pathFile = "E:\\Alina\\alinaEdiaProject\\java_basics\\Multithreading\\resources\\links.txt";

        LinkTree linkTree = new LinkTree(url);
        LinkTree linkTreeResult =new ForkJoinPool().invoke(new TreeRecursive(linkTree));

        try {
            FileOutputStream outPut = new FileOutputStream(pathFile);
            String s = linkWithIndent(linkTreeResult, 0);
            outPut.write(s.getBytes());
            outPut.flush();
            outPut.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    public static String linkWithIndent(LinkTree linkTree, int indent) {
        String t = "\t";
        String stringWithIndents = String.join("", t.repeat(indent));
        StringBuilder stringBuilder = new StringBuilder(stringWithIndents + linkTree.getUrl());
        for (LinkTree link : linkTree.getLinkChildren()) {
            stringBuilder.append("\n").append(linkWithIndent(link, indent + 1));
        }
        return stringBuilder.toString();
    }
}
