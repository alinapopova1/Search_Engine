package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

@Slf4j
public class LemmaFinder {
    private final LuceneMorphology luceneMorphology;
    private static final String WORD_TYPE_REGEX_RUS = "\\W\\w&&[^а-яА-Я\\s]";
    private static final String WORD_TYPE_REGEX_ENG = "\\W\\w&&[^a-zA-Z\\s]";
    private static final String[] particlesRusNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private static final String[] particlesEngNames = new String[]{"MC", "CONJ", "PART"};

    public static LemmaFinder getRusInstance() throws IOException {
        LuceneMorphology morphology = new RussianLuceneMorphology();
        return new LemmaFinder(morphology);
    }

    public static LemmaFinder getEngInstance() throws IOException {
        LuceneMorphology morphology = new EnglishLuceneMorphology();
        return new LemmaFinder(morphology);
    }

    private LemmaFinder(LuceneMorphology luceneMorphology) {
        this.luceneMorphology = luceneMorphology;
    }

    private LemmaFinder() {
        throw new RuntimeException("Disallow construct");
    }

    /**
     * Метод разделяет текст на слова, находит все леммы и считает их количество на русском.
     *
     * @param text текст из которого будут выбираться леммы
     * @return ключ является леммой, а значение количеством найденных лемм
     */
    public Map<String, Integer> collectRusLemmas(String text) {
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticleRus(wordBaseForms)) {
                continue;
            }

            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }

            String normalWord = normalForms.get(0);

            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }

        return lemmas;
    }

    /**
     * Метод разделяет текст на слова, находит все леммы и считает их количество на английском.
     *
     * @param text текст из которого будут выбираться леммы
     * @return ключ является леммой, а значение количеством найденных лемм
     */
    public Map<String, Integer> collectEngLemmas(String text) {
        String[] words = arrayContainsEnglishWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticleEng(wordBaseForms)) {
                continue;
            }

            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }

            String normalWord = normalForms.get(0);

            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }

        return lemmas;
    }


    /**
     * @param text текст из которого собираем все леммы
     * @return набор уникальных лемм найденных в тексте
     */
    public Set<String> getRusLemmaSet(String text) {
        String[] textArray = arrayContainsRussianWords(text);
        Set<String> lemmaSet = new HashSet<>();
        for (String word : textArray) {
            if (!word.isEmpty() && isCorrectWordFormRus(word)) {
                List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
                if (anyWordBaseBelongToParticleRus(wordBaseForms)) {
                    continue;
                }
                lemmaSet.addAll(luceneMorphology.getNormalForms(word));
            }
        }
        return lemmaSet;
    }

    /**
     * @param text английский текст из которого собираем все леммы
     * @return набор уникальных лемм найденных в тексте
     */
    public Set<String> getEngLemmaSet(String text) {
        String[] textArray = arrayContainsEnglishWords(text);
        Set<String> lemmaSet = new HashSet<>();
        for (String word : textArray) {
            if (!word.isEmpty() && isCorrectWordFormEng(word)) {
                List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
                if (anyWordBaseBelongToParticleEng(wordBaseForms)) {
                    continue;
                }
                lemmaSet.addAll(luceneMorphology.getNormalForms(word));
            }
        }
        return lemmaSet;
    }

    private boolean anyWordBaseBelongToParticleRus(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticlePropertyRus);
    }

    private boolean anyWordBaseBelongToParticleEng(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticlePropertyEng);
    }

    private boolean hasParticlePropertyRus(String wordBase) {
        for (String property : particlesRusNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasParticlePropertyEng(String wordBase) {
        for (String property : particlesEngNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
    }

    private String[] arrayContainsEnglishWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^a-z\\s])", " ")
                .trim()
                .split("\\s+");
    }

    private boolean isCorrectWordFormRus(String word) {
        List<String> wordInfo = luceneMorphology.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX_RUS)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCorrectWordFormEng(String word) {
        List<String> wordInfo = luceneMorphology.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX_ENG)) {
                return false;
            }
        }
        return true;
    }

    public String getLemmaByWord(String word){
        String wordLowerCase = word.toLowerCase();
        if (checkMatchWord(wordLowerCase)) return "";
        try {
            List<String> normalWordForms = luceneMorphology.getNormalForms(wordLowerCase);
            String wordInfo = luceneMorphology.getMorphInfo(wordLowerCase).toString();
            if (checkWordInfo(wordInfo)) return "";
            return normalWordForms.get(0);
        } catch (WrongCharaterException ex) {
            log.debug(ex.getMessage());
        }
        return "";
    }

    private boolean checkMatchWord(String word) {
        return word.isEmpty() || String.valueOf(word.charAt(0)).matches("[a-zA-Z]") || String.valueOf(word.charAt(0)).matches("[0-9]");
    }

    private boolean checkWordInfo(String wordInfo) {
        return wordInfo.contains("ПРЕДЛ") || wordInfo.contains("СОЮЗ") || wordInfo.contains("МЕЖД"); //|| wordInfo.contains("MC") || wordInfo.contains("CONJ") || wordInfo.contains("PART");
    }
}
