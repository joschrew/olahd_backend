package ola.hd.longtermstorage.elasticsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ola.hd.longtermstorage.model.Detail;
import ola.hd.longtermstorage.model.Facets;
import ola.hd.longtermstorage.model.HitList;
import ola.hd.longtermstorage.model.ResultSet;
import ola.hd.longtermstorage.model.Values;
import ola.hd.longtermstorage.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.springframework.util.CollectionUtils;

/**
 * Helper functions for the work with elasticsearch
 */
public class ElasticResponseHelper {

    private static final Object CREATOR_SEPARATOR = "; ";

    public ElasticResponseHelper() {
    }

    /**
     * Extract the results from the response and fill it into the response model.
     *
     * The query uses an aggregation to put the search hits belonging to one ocrd-zip together. The
     * results must be extracted from this aggregation. Additionally aggregations where queried for
     * the facets/filter-values. These are filled into a List of {@linkplain Facets} here and put
     * into the response model ({@linkplain ResultSet}.
     *
     * @return
     */
    public ResultSet responseToResultSet(SearchResponse response, String searchterm, boolean metadatasearch,
            boolean fulltextsearch, int offset, int limit) {
        Aggregations aggs = response.getAggregations();
        Terms hits = (Terms)aggs.get(ElasticQueryHelper.HITS_AGG);

        ResultSet res = putHitAggsIntoResponseModel(hits);
        List<Facets> facets = this.createFacetsFromAggs(aggs);
        res.setFacets(facets);
        res.setSearchTerm(searchterm);
        res.setMetadataSearch(metadatasearch);
        res.setFulltextSearch(fulltextsearch);
        res.setOffset(offset);
        res.setLimit(limit);
        return res;
    }

    /**
     * Fill the search hit from a detail-query into the response model
     *
     * @param hit
     * @return
     */
    public Detail fillSearchHitIntoDetail(Map<String, Object> hit) {
        Detail res = new Detail();
        res.setPID(hit.getOrDefault("pid", "").toString());
        res.setTitle(readTitleFromSearchHit(hit));
        res.setSubtitle(readSubtitleFromSearchHit(hit));

        res.setPublisher(readPublisherFromSearchHit(hit));
        res.setYearOfPublish(readYearFromSearchHit(hit));
        res.setPlaceOfPublish(readPlaceOfPublishFromSearchHit(hit));
        res.setCreator(readCreatorFromSearchHit(hit));
        res.setGT(readIsGtFromSearchHit(hit));

        return res;
    }



    /**
     * Convert aggregations with the hits to ResultSet as specified by the API
     *
     * @param hits
     * @return
     */
    private ResultSet putHitAggsIntoResponseModel(Terms hits) {
        ResultSet res = new ResultSet();
        List<HitList> hitlist = new ArrayList<>();
        res.setHitlist(hitlist);

        for (Bucket hit: hits.getBuckets()) {
            HitList hitResult = new HitList();
            hitlist.add(hitResult);
            Terms sub1agg = hit.getAggregations().get("group-by-log");
            // TODO: delete next two lines
            assert sub1agg.getBuckets().size() == 1;
            Bucket sub1 = sub1agg.getBuckets().get(0);
            TopHits sub2agg = (TopHits)sub1.getAggregations().get("by_top_hits");
            // TODO: delete next line
            assert sub2agg.getHits().getTotalHits() == 1;
            Map<String, Object> hitmap = sub2agg.getHits().getAt(0).getSourceAsMap();

            hitResult.setPid(hit.getKeyAsString());
            hitResult.setTitle(readTitleFromSearchHit(hitmap));
            hitResult.setSubtitle(readSubtitleFromSearchHit(hitmap));
            hitResult.setPlaceOfPublish(readPlaceOfPublishFromSearchHit(hitmap));
            hitResult.setYearOfPublish(readYearFromSearchHit(hitmap));
            hitResult.setPublisher(readPublisherFromSearchHit(hitmap));
            hitResult.setCreator(readCreatorFromSearchHit(hitmap));
            hitResult.setGt(readIsGtFromSearchHit(hitmap));
        }
        res.setHits(hitlist.size());
        return res;
    }

    /**
     * Fill aggregation-values from Elasticsearch into objects of the response model (Facets).
     *
     * @param aggs
     * @return
     */
    private List<Facets> createFacetsFromAggs(Aggregations aggs) {
        List<Facets> facets = new ArrayList<>();
        if (aggs == null) {
            return facets;
        }
        Map<String, Aggregation> map = aggs.getAsMap();
        if (CollectionUtils.isEmpty(map)) {
            return facets;
        }

        for (Map.Entry<String, Aggregation> entry : map.entrySet()) {
            if (entry.getKey().equals(ElasticQueryHelper.HITS_AGG)) {
                continue;
            }
            List<Values> values = new ArrayList<>();
            Terms terms = (Terms)entry.getValue();
            List<? extends Bucket> buckets = terms.getBuckets();
            for (Bucket x : buckets) {
                Values val = new Values(x.getKeyAsString(), (int)x.getDocCount());
                values.add(val);
            }
            facets.add(new Facets(terms.getName(), values));
        }
        return facets;
    }

    /**
     * Extract the year from a Elasticsearch search hit to the logical entry
     *
     * @param hit - response from Elasticsearch query
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static int readYearFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> infos = (Map)hit.get("publish_infos");
            Integer i = (Integer)infos.get("year_publish");
            if (i != null) {
                return i;
            }
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return -1;
    }

    /**
     * Extract the publisher from a elasticsearch search hit to the logical entry
     *
     * @param hit
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readPublisherFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> infos = (Map)hit.get("publish_infos");
            List<String> publisher = (List<String>)infos.get("publisher");
            if (!publisher.isEmpty()) {
                return publisher.stream().map(String::trim).collect(Collectors.joining(","));
            }
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return "";
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readPlaceOfPublishFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> infos = (Map)hit.get("publish_infos");
            List<String> places = (List<String>)infos.get("place_publish");
            for (String s : places) {
                if (StringUtils.isNotBlank(s)) {
                    return s;
                }
            }
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return "";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readSubtitleFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> title = (Map)hit.get("title");
            if (title.containsKey("subtitle")) {
                Object object = title.get("subtitle");
                if (object != null && object instanceof String) {
                    return object.toString();
                }
            }
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return "";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readCreatorFromSearchHit(Map<String, Object> hit) {
        try {
            List<Map<String, Object>> infos = (List)hit.get("creator_infos");
            StringBuilder result = new StringBuilder();
            for (Map<String, Object> creator : infos) {
                if (creator.containsKey("name")) {
                    Object object = creator.get("name");
                    if (object != null && object instanceof String) {
                        if (!result.toString().isBlank()) {
                            result.append(CREATOR_SEPARATOR);
                        }
                        result.append(object.toString());
                    }
                }
            }
            return result.toString();
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return "";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readTitleFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> title = (Map)hit.get("title");
            if (title.containsKey("title")) {
                Object object = title.get("title");
                if (object != null && object instanceof String) {
                    return object.toString();
                }
            }
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return "";
    }

    private static Boolean readIsGtFromSearchHit(Map<String, Object> hit) {
        try {
            return Utils.stringToBool(hit.get("IsGt").toString());
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return null;
    }
}
