package lahds.relyme.UserInterface.EmojiView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import android.text.Spannable;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import lahds.relyme.UserInterface.EmojiView.Emoji.EmojiLoader;
import lahds.relyme.UserInterface.EmojiView.Emoji.Emoji;
import lahds.relyme.UserInterface.EmojiView.Emoji.EmojiCategory;
import lahds.relyme.UserInterface.EmojiView.Emoji.EmojiProvider;
import lahds.relyme.UserInterface.EmojiView.Listener.EditTextInputListener;
import lahds.relyme.UserInterface.EmojiView.Listener.EmojiVariantCreatorListener;
import lahds.relyme.UserInterface.EmojiView.Listener.OnEmojiActions;
import lahds.relyme.UserInterface.EmojiView.Listener.StickerViewCreatorListener;
import lahds.relyme.UserInterface.EmojiView.Shared.RecentEmoji;
import lahds.relyme.UserInterface.EmojiView.Shared.RecentEmojiManager;
import lahds.relyme.UserInterface.EmojiView.Shared.VariantEmoji;
import lahds.relyme.UserInterface.EmojiView.Shared.VariantEmojiManager;
import lahds.relyme.UserInterface.EmojiView.Sticker.RecentSticker;
import lahds.relyme.UserInterface.EmojiView.Sticker.RecentStickerManager;
import lahds.relyme.UserInterface.EmojiView.Sticker.Sticker;
import lahds.relyme.UserInterface.EmojiView.Sticker.StickerCategory;
import lahds.relyme.UserInterface.EmojiView.Utils.EmojiRange;
import lahds.relyme.UserInterface.EmojiView.Utils.EmojiReplacer;
import lahds.relyme.UserInterface.EmojiView.Utils.EmojiSpan;
import lahds.relyme.UserInterface.EmojiView.Utils.Utils;
import lahds.relyme.UserInterface.EmojiView.Variant.EmojiVariantPopup;
import lahds.relyme.UserInterface.EmojiView.Variant.SimpleEmojiVariantPopup;
import lahds.relyme.UserInterface.EmojiView.Variant.TouchEmojiVariantPopup;
import lahds.relyme.UserInterface.EmojiView.View.EmojiBase;
import lahds.relyme.UserInterface.EmojiView.View.EmojiView;
import lahds.relyme.UserInterface.EmojiView.View.SingleEmojiView;
import lahds.relyme.UserInterface.EmojiView.View.StickerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Amir Hossein Aghajari
 * @version 1.4.0
 */
public class EmojiManager {

    private static boolean ripple = true;

    private static EditTextInputListener defaultInputListener = new EditTextInputListener() {
        @Override
        public void input(@NonNull final EditText editText, @Nullable final Emoji emoji) {
            if (emoji != null) {
                final int start = editText.getSelectionStart();
                final int end = editText.getSelectionEnd();

                if (start < 0) {
                    editText.append(emoji.getUnicode());
                } else {
                    editText.getText().replace(Math.min(start, end), Math.max(start, end), emoji.getUnicode(), 0, emoji.getUnicode().length());
                }
            }
        }
    };

    private static StickerViewCreatorListener defaultStickerCreator = new StickerViewCreatorListener() {
        @Override
        public View onCreateStickerView(@NonNull Context context, @Nullable StickerCategory category, boolean isRecent) {
            return new AppCompatImageView(context);
        }

        @Override
        public View onCreateCategoryView(@NonNull Context context) {
            return new AppCompatImageView(context);
        }
    };

    private static EditTextInputListener inputListener;
    private static StickerViewCreatorListener stickerViewCreatorListener;
    private static EmojiVariantCreatorListener emojiVariantCreatorListener;

    private EmojiManager() {
    }

    private static final Comparator<String> STRING_LENGTH_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(final String first, final String second) {
            final int firstLength = first.length();
            final int secondLength = second.length();

            return firstLength < secondLength ? 1 : firstLength == secondLength ? 0 : -1;
        }
    };

    private static final int GUESSED_UNICODE_AMOUNT = 3000;
    private static final int GUESSED_TOTAL_PATTERN_LENGTH = GUESSED_UNICODE_AMOUNT * 4;


    private static final EmojiReplacer DEFAULT_EMOJI_REPLACER = new EmojiReplacer() {
        @Override
        public void replaceWithImages(final Context context, final View view, final Spannable text, final float emojiSize, final Paint.FontMetrics fontMetrics) {
            if (text.length() == 0) return;
            final EmojiManager emojiManager = EmojiManager.getInstance();
            final EmojiSpan[] existingSpans = text.getSpans(0, text.length(), EmojiSpan.class);
            final List<Integer> existingSpanPositions = new ArrayList<>(existingSpans.length);

            final int size = existingSpans.length;
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < size; i++) {
                existingSpanPositions.add(text.getSpanStart(existingSpans[i]));
            }

            final List<EmojiRange> findAllEmojis = emojiManager.findAllEmojis(text);

            for (int i = 0; i < findAllEmojis.size(); i++) {
                final EmojiRange location = findAllEmojis.get(i);

                if (!existingSpanPositions.contains(location.start)) {
                    text.setSpan(new EmojiSpan(context, location.emoji, emojiSize),
                            location.start, location.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
    };

    final Map<String, Emoji> emojiMap = new LinkedHashMap<>(GUESSED_UNICODE_AMOUNT);
    private EmojiCategory[] categories;
    private Pattern emojiPattern;
    private Pattern emojiRepetitivePattern;

    private EmojiReplacer emojiReplacer;

    @SuppressLint("StaticFieldLeak")
    static EmojiManager INSTANCE = null;
    @SuppressLint("StaticFieldLeak")
    private static Context context;

    public static EmojiManager getInstance() {
        return INSTANCE;
    }

    public static boolean isInstalled() {
        return INSTANCE != null;
    }

    private EmojiProvider provider;


    /**
     * Installs the given EmojiProvider.
     *
     * @param provider the provider that should be installed.
     */
    public static void install(Context context, final EmojiProvider provider) {
        EmojiManager.context = context.getApplicationContext();
        if (INSTANCE != null) destroy();
        INSTANCE = new EmojiManager();
        if (mEmojiTheme == null) mEmojiTheme = new EmojiTheme();
        if (mStickerTheme == null) mStickerTheme = new EmojiTheme();
        /**recentEmoji = null;
         recentSticker = null;
         variantEmoji = null;
         emojiLoader = null;*/
        INSTANCE.provider = provider;

        setMaxRecentSize(48);
        setMaxStickerRecentSize(Utils.getStickerGridCount(context) * 3);
        INSTANCE.categories = provider.getCategories();
        INSTANCE.emojiMap.clear();
        INSTANCE.emojiReplacer = provider instanceof EmojiReplacer ? (EmojiReplacer) provider : DEFAULT_EMOJI_REPLACER;
        if (inputListener == null) inputListener = defaultInputListener;
        if (stickerViewCreatorListener == null) stickerViewCreatorListener = defaultStickerCreator;
        if (emojiVariantCreatorListener == null) enableTouchEmojiVariantPopup();

        final List<String> unicodesForPattern = new ArrayList<>(GUESSED_UNICODE_AMOUNT);

        final int categoriesSize = INSTANCE.categories.length;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < categoriesSize; i++) {
            final Emoji[] emojis = INSTANCE.categories[i].getEmojis();

            final int emojisSize = emojis.length;
            //noinspection ForLoopReplaceableByForEach
            for (int j = 0; j < emojisSize; j++) {
                final Emoji emoji = emojis[j];
                final String unicode = emoji.getUnicode();
                final List<Emoji> variants = emoji.getVariants();

                INSTANCE.emojiMap.put(unicode, emoji);
                unicodesForPattern.add(unicode);

                //noinspection ForLoopReplaceableByForEach
                for (int k = 0; k < variants.size(); k++) {
                    final Emoji variant = variants.get(k);
                    final String variantUnicode = variant.getUnicode();

                    INSTANCE.emojiMap.put(variantUnicode, variant);
                    unicodesForPattern.add(variantUnicode);
                }
            }
        }

        if (unicodesForPattern.isEmpty()) {
            throw new IllegalArgumentException("Your EmojiProvider must at least have one category with at least one emoji.");
        }

        // We need to sort the unicodes by length so the longest one gets matched first.
        Collections.sort(unicodesForPattern, STRING_LENGTH_COMPARATOR);

        final StringBuilder patternBuilder = new StringBuilder(GUESSED_TOTAL_PATTERN_LENGTH);

        final int unicodesForPatternSize = unicodesForPattern.size();
        for (int i = 0; i < unicodesForPatternSize; i++) {
            patternBuilder.append(Pattern.quote(unicodesForPattern.get(i))).append('|');
        }

        final String regex = patternBuilder.deleteCharAt(patternBuilder.length() - 1).toString();
        INSTANCE.emojiPattern = Pattern.compile(regex);
        INSTANCE.emojiRepetitivePattern = Pattern.compile('(' + regex + ")+");
    }

    /**
     * Destroys the EmojiManager. This means that all internal data structures are released as well as
     * all data associated with installed Emojis.
     * <p>
     * You have to install EmojiManager again (if you wanted to use it)
     */
    public static void destroy() {
        synchronized (EmojiManager.class) {
            INSTANCE.provider.destroy();
            INSTANCE.emojiMap.clear();
            INSTANCE.categories = null;
            INSTANCE.emojiPattern = null;
            INSTANCE.emojiRepetitivePattern = null;
            INSTANCE.emojiReplacer = null;
        }
    }

    public void replaceWithImages(final Context context, View view, final Spannable text, final float emojiSize, final Paint.FontMetrics fontMetrics) {
        if (INSTANCE == null) return;
        emojiReplacer.replaceWithImages(context, view, text, emojiSize, fontMetrics);
    }

    public void replaceWithImages(final Context context, final Spannable text, final float emojiSize, final Paint.FontMetrics fontMetrics) {
        if (INSTANCE == null) return;
        emojiReplacer.replaceWithImages(context, null, text, emojiSize, fontMetrics);
    }

    public EmojiCategory[] getCategories() {
        return categories;
    }

    Pattern getEmojiRepetitivePattern() {
        return emojiRepetitivePattern;
    }

    public List<EmojiRange> findAllEmojis(final CharSequence text) {
        final List<EmojiRange> result = new ArrayList<>();

        if (!TextUtils.isEmpty(text)) {
            final Matcher matcher = emojiPattern.matcher(text);

            while (matcher.find()) {
                final Emoji found = findEmoji(text.subSequence(matcher.start(), matcher.end()));

                if (found != null) {
                    result.add(new EmojiRange(matcher.start(), matcher.end(), found));
                }
            }
        }

        return result;
    }

    public Emoji findEmoji(final CharSequence candidate) {
        // We need to call toString on the candidate, since the emojiMap may not find the requested entry otherwise, because
        // the type is different.
        return emojiMap.get(candidate.toString());
    }

    static EmojiTheme mEmojiTheme;

    /**
     * set EmojiView theme settings
     */
    public static void setEmojiViewTheme(EmojiTheme theme) {
        mEmojiTheme = theme;
    }

    /**
     * @return EmojiView theme settings
     */
    public static EmojiTheme getEmojiViewTheme() {
        return mEmojiTheme;
    }

    /**
     * use EmojiManager.getEmojiViewTheme() instead.
     *
     * @deprecated
     */
    public static EmojiTheme getTheme() {
        return getEmojiViewTheme();
    }

    static EmojiTheme mStickerTheme;

    /**
     * set StickerView theme settings
     */
    public static void setStickerViewTheme(EmojiTheme theme) {
        mStickerTheme = theme;
    }

    /**
     * @return StickerView theme settings
     */
    public static EmojiTheme getStickerViewTheme() {
        return mStickerTheme;
    }

    static boolean footer = true;

    /**
     * EmojiPager footer view. backspace will add on footer right icon.
     *
     * @param footer
     */
    public static void setFooterEnabled(boolean footer) {
        EmojiManager.footer = footer;
    }

    public static boolean isFooterEnabled() {
        return footer;
    }

    /**
     * set max emoji recent sizes in default RecentEmojiManager
     *
     * @see RecentEmojiManager
     */
    public static void setMaxRecentSize(int size) {
        RecentEmojiManager.MAX_RECENT = size;
    }

    public static int getMaxRecentSize() {
        return RecentEmojiManager.MAX_RECENT;
    }

    /**
     * fill recent history with default values (if recent was empty)
     * default is true
     *
     * @see RecentEmojiManager
     */
    public static void setFillRecentHistoryEnabled(boolean enabled) {
        RecentEmojiManager.FILL_DEFAULT_HISTORY = enabled;
    }

    /**
     * fill recent history with this values if recent was empty.
     *
     * @see RecentEmojiManager
     */
    public static void setFillRecentHistoryData(String[] newRecent) {
        RecentEmojiManager.FILL_DEFAULT_RECENT_DATA = newRecent;
    }

    public static String[] getFillRecentHistoryData() {
        return RecentEmojiManager.FILL_DEFAULT_RECENT_DATA;
    }

    /**
     * set max sticker recent sizes in default RecentStickerManager
     *
     * @see RecentStickerManager
     */
    public static void setMaxStickerRecentSize(int size) {
        RecentStickerManager.MAX_RECENTS = size;
    }

    public static int getMaxStickerRecentSize() {
        return RecentStickerManager.MAX_RECENTS;
    }

    static boolean recentVariant = true;

    /**
     * show emoji variants in recent tab
     */
    public static void setRecentVariantEnabled(boolean enabled) {
        recentVariant = enabled;
    }

    public static boolean isRecentVariantEnabled() {
        return recentVariant;
    }

    static boolean showEmptyRecent = false;

    /**
     * Show Recent Tab while there is no data to show
     * you can manage this with isEmptyA() method in RecentManagers
     */
    public static void setShowEmptyRecentEnabled(boolean value) {
        showEmptyRecent = value;
    }

    public static boolean isShowingEmptyRecentEnabled() {
        return showEmptyRecent;
    }

    private static boolean asyncLoad = false;

    public static boolean isAsyncLoadEnabled() {
        return asyncLoad;
    }


    /**
     * load emojis with an async task
     * default is true;
     */
    public static void setAsyncLoadEnabled(boolean asyncLoad) {
        EmojiManager.asyncLoad = asyncLoad;
    }

    static RecentEmoji recentEmoji;
    static RecentSticker recentSticker;
    static VariantEmoji variantEmoji;


    /**
     * set EmojiView EmojiRecentManager
     */
    public static void setRecentEmoji(RecentEmoji recentEmoji) {
        EmojiManager.recentEmoji = recentEmoji;
    }

    /**
     * set EmojiView StickerRecentManager
     */
    public static void setRecentSticker(RecentSticker recentSticker) {
        EmojiManager.recentSticker = recentSticker;
    }

    /**
     * set EmojiView VariantManager
     */
    public static void setVariantEmoji(VariantEmoji variantEmoji) {
        EmojiManager.variantEmoji = variantEmoji;
    }

    public static RecentEmoji getRecentEmoji() {
        if (recentEmoji == null) return new RecentEmojiManager(context);
        return recentEmoji;
    }

    public static RecentSticker getRecentSticker(final String defType) {
        if (recentSticker == null) return new RecentStickerManager(context, defType);
        return recentSticker;
    }

    public static RecentSticker getRecentSticker() {
        return recentSticker;
    }

    public static VariantEmoji getVariantEmoji() {
        if (variantEmoji == null) return new VariantEmojiManager(context);
        return variantEmoji;
    }

    /**
     * check EmojiBase is instance of EmojiView or SingleEmojiView
     */
    public static boolean isEmojiView(EmojiBase base) {
        return base instanceof EmojiView || base instanceof SingleEmojiView;
    }

    /**
     * check EmojiBase is instance of StickerView
     */
    public static boolean isStickerView(EmojiBase base) {
        return base instanceof StickerView;
    }

    static EmojiLoader emojiLoader;

    /**
     * set EmojiView EmojiLoader
     */
    public static void setEmojiLoader(EmojiLoader emojiLoader) {
        EmojiManager.emojiLoader = emojiLoader;
    }

    /**
     * set Emoji replacer
     */
    public void setEmojiReplacer(EmojiReplacer emojiReplacer) {
        this.emojiReplacer = emojiReplacer;
        if (emojiReplacer == null) {
            this.emojiReplacer = EmojiManager.DEFAULT_EMOJI_REPLACER;
        }
    }

    /**
     * @return the installed emoji provider
     */
    public EmojiProvider getEmojiProvider() {
        return provider;
    }

    public static void setEditTextInputListener(EditTextInputListener listener) {
        EmojiManager.inputListener = listener;
        if (listener == null) {
            EmojiManager.inputListener = EmojiManager.defaultInputListener;
        }
    }

    public static void setStickerViewCreatorListener(StickerViewCreatorListener listener) {
        EmojiManager.stickerViewCreatorListener = listener;
        if (listener == null) {
            EmojiManager.stickerViewCreatorListener = EmojiManager.defaultStickerCreator;
        }
    }

    public EditTextInputListener getEditTextInputListener() {
        if (inputListener == null) return defaultInputListener;
        return inputListener;
    }

    public StickerViewCreatorListener getStickerViewCreatorListener() {
        if (stickerViewCreatorListener == null) return defaultStickerCreator;
        return stickerViewCreatorListener;
    }

    public static void setEmojiVariantCreatorListener(EmojiVariantCreatorListener listener) {
        if (listener == null) {
            enableTouchEmojiVariantPopup();
            return;
        }
        EmojiManager.emojiVariantCreatorListener = listener;
    }

    public static void enableTouchEmojiVariantPopup() {
        EmojiManager.emojiVariantCreatorListener = new EmojiVariantCreatorListener() {
            @Override
            public EmojiVariantPopup create(@NonNull View rootView, @Nullable OnEmojiActions listener) {
                return new TouchEmojiVariantPopup(rootView, listener);
            }
        };
    }

    public static void enableSimpleEmojiVariantPopup() {
        EmojiManager.emojiVariantCreatorListener = new EmojiVariantCreatorListener() {
            @Override
            public EmojiVariantPopup create(@NonNull View rootView, @Nullable OnEmojiActions listener) {
                return new SimpleEmojiVariantPopup(rootView, listener);
            }
        };
    }

    public static EmojiVariantCreatorListener getEmojiVariantCreatorListener() {
        if (emojiVariantCreatorListener == null) enableTouchEmojiVariantPopup();
        return emojiVariantCreatorListener;
    }

    public static EmojiLoader getEmojiLoader() {
        return emojiLoader;
    }

    /**
     * disable recent managers
     */
    public static void disableRecentManagers() {
        disableEmojiRecentManager();
        disableStickerRecentManager();
    }

    /**
     * disable sticker recent manager
     */
    public static void disableStickerRecentManager() {
        recentEmoji = new RecentEmoji() {
            @NonNull
            @Override
            public Collection<Emoji> getRecentEmojis() {
                return Arrays.asList(new Emoji[0]);
            }

            @Override
            public void addEmoji(@NonNull Emoji emoji) {
            }

            @Override
            public void persist() {
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public void reload() {
            }
        };
    }

    /**
     * disable emoji recent manager
     */
    public static void disableEmojiRecentManager() {
        recentSticker = new RecentSticker() {
            @SuppressWarnings("rawtypes")
            @NonNull
            @Override
            public Collection<Sticker> getRecentStickers() {
                return Arrays.asList(new Sticker[0]);
            }

            @Override
            public void addSticker(@SuppressWarnings("rawtypes") @NonNull Sticker sticker) {
            }

            @Override
            public void persist() {
            }

            @Override
            public boolean isEmpty() {
                return true;
            }
        };
    }

    public static void enableDefaultRecentManagers() {
        enableDefaultEmojiRecentManager();
        enableDefaultStickerRecentManager();
    }

    public static void enableDefaultStickerRecentManager() {
        recentSticker = null;
    }

    public static void enableDefaultEmojiRecentManager() {
        recentEmoji = null;
    }

    public static boolean isRippleEnabled() {
        return ripple;
    }

    public static void setRippleEnabled(boolean enabled) {
        ripple = enabled;
    }

    static boolean isUsingPopupWindow = false;

    public static void setUsingPopupWindow(boolean isUsingPopupWindow) {
        EmojiManager.isUsingPopupWindow = isUsingPopupWindow;
    }

    public static boolean isUsingPopupWindow() {
        return isUsingPopupWindow;
    }

    private static boolean backspaceCategoryEnabled = true;

    public static boolean isBackspaceCategoryEnabled() {
        return backspaceCategoryEnabled;
    }

    public static void setBackspaceCategoryEnabled(boolean enabled) {
        backspaceCategoryEnabled = enabled;
    }

    public static void resetTheme() {
        setEmojiViewTheme(new EmojiTheme());
        setStickerViewTheme(new EmojiTheme());
    }
}
