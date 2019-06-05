package com.streamlio.sentiment;

import com.twitter.twittertext.Extractor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.functions.api.Context;
import org.apache.pulsar.functions.api.Function;
import org.apache.pulsar.io.twitter.data.TweetData;

import static com.streamlio.sentiment.NLP.findSentiment;

@Slf4j
public class SentimentAnalysisFunction implements Function<TweetData, Void> {

    private boolean isFirst = true;

    @Override
    public Void process(TweetData tweetData, Context context) throws Exception {
        if (isFirst) {
            NLP.init();
            isFirst = false;
        }

        if (tweetData.getText() != null && tweetData.getText().length() > 0) {
            if (tweetData.getLang() != null && tweetData.getLang().equals("en")) {
                String cleanedTweet = cleanTweet(tweetData.getText());
                if (cleanedTweet != null && cleanedTweet.length() > 0 && !cleanedTweet.equals(" ")) {
                    int sentimentScore = findSentiment(cleanedTweet);

                    // negative sentiment
                    if (sentimentScore <= 0) {
                        context.publish("negative_tweets", tweetData);
                        context.incrCounter("negative_tweets", 1);
                    } else if (sentimentScore >= 1 && sentimentScore <= 2) {
                        // neutral
                        context.publish("neutral_tweets", tweetData);
                        context.incrCounter("neutral_tweets", 1);
                    } else if (sentimentScore >= 3) {
                        // positive
                        context.publish("positive_tweets", tweetData);
                        context.incrCounter("positive_tweets", 1);
                    } else {
                        log.error("Tweet: {} generator unexpected sentiment score: {}", tweetData.getText(),
                                sentimentScore);

                    }
                }
            }
        }

        return null;
    }
    public static String cleanTweet(String tweet) {
        final Extractor extractor = new Extractor();

        String cleanedTweet = tweet.replaceAll("[^\\x20-\\x7e]", "").trim();

        if (cleanedTweet.startsWith("RT")) {
            cleanedTweet = cleanedTweet.substring(2);
        }

        for (String entry : extractor.extractURLs(tweet)) {
            cleanedTweet = cleanedTweet.replace(entry, "");
        }

        for (String entry : extractor.extractHashtags(tweet)) {
            cleanedTweet = cleanedTweet.replace("#" + entry, entry);
        }


        for (String entry : extractor.extractMentionedScreennames(tweet)) {
            cleanedTweet = cleanedTweet.replaceAll(String.format("@%s*.\\s",  entry), "");

        }

        return cleanedTweet.trim();
    }

}
