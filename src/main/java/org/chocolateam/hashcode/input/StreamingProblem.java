package org.chocolateam.hashcode.input;

import java.util.Map.Entry;

import org.chocolateam.hashcode.model.Cache;
import org.chocolateam.hashcode.model.ScoredVideo;
import org.chocolateam.hashcode.model.Video;
import org.chocolateam.hashcode.output.Solution;
import org.chocolateam.hashcode.util.Queue;
import org.jetbrains.annotations.Nullable;

public class StreamingProblem {

    public int nVideos;

    public int nEndpoints;

    public int nRequestDescriptions;

    public int nCaches;

    public int cacheSize;

    public int[] videoSizes;

    public Endpoint[] endpoints;

    public RequestDesc[] requestDescs;

    public Cache[] caches;
    public Video[] videos;

    public Solution solve() {
        System.out.println(String.format("Solving for %d videos, %d caches, %d endpoints", nVideos, nCaches,
                nEndpoints));
        createCaches();
        createVideos();
        computeInitialGains();
        scoreVideos();

        while (findAndInsertMax()) {
        }

        return new Solution(caches);
    }

    private void createCaches() {
        caches = new Cache[nCaches];
        for (int i = 0; i < nCaches; i++) {
            caches[i] = new Cache(i, cacheSize);
        }
    }

    private void createVideos() {
        videos = new Video[nVideos];
        for (int i = 0; i < nVideos; i++) {
            videos[i] = new Video(i, videoSizes[i]);
        }
    }

    private void computeInitialGains() {
        System.out.println("Computing gain for each cache and video...");
        for (RequestDesc rd : requestDescs) {
            readRequest(rd);
        }
    }

    private void readRequest(RequestDesc requestDesc) {
        Endpoint endpoint = endpoints[requestDesc.endpointId];
        Video video = videos[requestDesc.videoId];

        endpoint.addRequests(video, requestDesc.count);

        for (Entry<Integer, Integer> gainForCache : endpoint.gainPerCache.entrySet()) {
            int cacheId = gainForCache.getKey();

            // for graph
            Cache cache = caches[cacheId];
            cache.endpoints.add(endpoint);

            int gainPerRequest = gainForCache.getValue();
            int totalGain = requestDesc.count * gainPerRequest;
            cache.addGainForVideo(video, totalGain);
        }
    }

    private void scoreVideos() {
        System.out.println("Computing score for each video in each cache...");
        for (Cache cache : caches) {
            System.out.println("Computing score for each video in cache " + cache);
            cache.scoreVideos();
        }
    }

    private boolean findAndInsertMax() {
        Cache maxCache = getCacheWithBestVideo();
        if (maxCache == null) {
            System.out.println("We're done.");
            return false;
        }
        ScoredVideo maxRankVideo = maxCache.scoredVideos.poll();
        if (maxCache.canHold(maxRankVideo)) {
            cacheVideo(maxCache, maxRankVideo);
            System.out.println(String.format(
                    "Inserted video %s in cache %3d (%5.1f%% full, %3d videos)  %6.2f%% total cache capacity",
                    maxRankVideo, maxCache.id, maxCache.getCurrentCacheUsage(cacheSize), maxCache.getNbVideosInCache(),
                    getOverallCacheUsage()));
            if (maxCache.scoredVideos.isEmpty()) {
                System.out.println("Queue for cache " + maxCache + " exhausted");
            }
        }
        return true;
    }

    private double getOverallCacheUsage() {
        long totalRemainingCapacity = 0;
        for (Cache cache : caches) {
            totalRemainingCapacity += cache.remainingCapacity;
        }
        long totalCacheCapacity = cacheSize * caches.length;
        return (double)(totalCacheCapacity - totalRemainingCapacity) / totalCacheCapacity;
    }

    @Nullable
    private Cache getCacheWithBestVideo() {
        Cache cacheWithBestVideo = null;
        double globalMaxScore = Double.NEGATIVE_INFINITY;
        for (Cache cache : caches) {
            if (cache.scoredVideos.isEmpty()) {
                continue;
            }
            ScoredVideo localMaxVideo = cache.scoredVideos.peek();
            if (cacheWithBestVideo == null || localMaxVideo.score > globalMaxScore) {
                cacheWithBestVideo = cache;
                globalMaxScore = localMaxVideo.score;
            }
        }
        return cacheWithBestVideo;
    }

    private void cacheVideo(Cache maxCache, ScoredVideo maxRankVideo) {
        maxCache.store(maxRankVideo.video);
        reduceScoreInOtherCaches(maxCache, maxRankVideo);
    }

    private void reduceScoreInOtherCaches(Cache destinationCache, ScoredVideo video) {
        for (Endpoint endpoint : destinationCache.endpoints) {
            int alreadyGained = endpoint.gainPerCache.get(destinationCache.id);
            long nRequestsForVid = endpoint.getNbRequests(video.video);
            if (nRequestsForVid == 0) {
                continue; // no request for this video in this endpoint
            }
            long overestimationInGain = alreadyGained * nRequestsForVid;
            double overestimationInRank = overestimationInGain / video.video.size;
            for (Entry<Integer, Integer> entry : endpoint.gainPerCache.entrySet()) {
                int epCacheId = entry.getKey();
                if (destinationCache.id != epCacheId) {
                    removeOverestimatedGain(video, overestimationInRank, caches[epCacheId]);
                }
            }
        }
    }

    private void removeOverestimatedGain(ScoredVideo video, double overestimationInRank, Cache cache) {
        Queue<ScoredVideo> queue = cache.scoredVideos;
        ScoredVideo videoInThisCache = queue.removeSimilar(video);
        if (videoInThisCache == null) {
            System.out.println(String.format("Video %d already stored in cache %d, not in queue anymore", video.video
                    .id, cache.id));
            return;
        }
        System.out.println(
                String.format("Removing %5.2f score for video %s for cache %d", overestimationInRank, video, cache.id));
        videoInThisCache.score -= overestimationInRank;
        if (videoInThisCache.score < 0) {
            videoInThisCache.score = 0;
        }
        queue.add(videoInThisCache);
    }
}
