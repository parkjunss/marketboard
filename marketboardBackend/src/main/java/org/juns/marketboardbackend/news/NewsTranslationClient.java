package org.juns.marketboardbackend.news;

import java.util.List;

public interface NewsTranslationClient {
    /** Returns translations in input order, or an empty list when translation is unavailable. */
    List<String> translateToKorean(List<String> texts);
}
