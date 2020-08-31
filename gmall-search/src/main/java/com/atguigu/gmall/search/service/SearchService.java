package com.atguigu.gmall.search.service;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SearchResponseVo search(SearchParamVo paramVo) {
        try {
            SearchSourceBuilder sourceBuilder = this.buildDSL(paramVo);
            SearchResponse response = this.restHighLevelClient.search(new SearchRequest(new String[]{"goods"}, sourceBuilder), RequestOptions.DEFAULT);
            SearchResponseVo responseVo = this.parseSearchResult(response);
            // 通过查询条件获取分页数据
            responseVo.setPageNum(paramVo.getPageNum());
            responseVo.setPageSize(paramVo.getPageSize());
            return responseVo;
        } catch (IOException e) {
            // 打广告：TODO
            e.printStackTrace();
        }
        return null;
    }

    private SearchResponseVo parseSearchResult(SearchResponse response){
        SearchResponseVo responseVo = new SearchResponseVo();

        // 解析搜索数据中的hits
        SearchHits hits = response.getHits();
        // 获取了总记录数
        responseVo.setTotal(hits.getTotalHits());

        // 获取当前页数据
        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Arrays.stream(hitsHits).map(hitsHit -> {
            try {
                // 获取hit中的_source
                String json = hitsHit.getSourceAsString();
                // 反序列化，获取了Goods对象
                Goods goods = MAPPER.readValue(json, Goods.class);

                // 获取高亮标题替换掉普通title
                HighlightField highlightField = hitsHit.getHighlightFields().get("title");
                Text[] fragments = highlightField.getFragments();
                goods.setTitle(fragments[0].string());
                return goods;
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());
        responseVo.setGoodsList(goodsList);

        // 解析搜索数据中的聚合数据
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();

        // 获取品牌聚合，解析出品牌集合
        ParsedLongTerms brandIdAgg = (ParsedLongTerms)aggregationMap.get("brandIdAgg");
        List<? extends Terms.Bucket> brandBuckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(brandBuckets)){
            List<BrandEntity> brandEntities = brandBuckets.stream().map(bucket -> {
                BrandEntity brandEntity = new BrandEntity();
                // 获取桶中的key，设置给品牌id
                brandEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());

                // 获取桶中的所有子聚合
                Map<String, Aggregation> brandAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                // 获取品牌名称的子聚合
                ParsedStringTerms brandNameAgg = (ParsedStringTerms)brandAggregationMap.get("brandNameAgg");
                // 获取品牌名称子聚合中的桶
                List<? extends Terms.Bucket> nameBuckets = brandNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameBuckets)) {
                    brandEntity.setName(nameBuckets.get(0).getKeyAsString());
                }

                // 获取logo子聚合
                ParsedStringTerms logoAgg = (ParsedStringTerms) brandAggregationMap.get("logoAgg");
                // 获取logo子聚合中桶，有且仅有一个桶，获取桶中的key
                List<? extends Terms.Bucket> logoBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoBuckets)){
                    brandEntity.setLogo(logoBuckets.get(0).getKeyAsString());
                }

                return brandEntity;
            }).collect(Collectors.toList());
            responseVo.setBrands(brandEntities);
        }

        // 获取分类id的聚合
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms)aggregationMap.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryBuckets)){
            List<CategoryEntity> categoryEntities = categoryBuckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();
                categoryEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());

                // 获取分类名称的子聚合
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms)((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                List<? extends Terms.Bucket> nameBuckets = categoryNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameBuckets)){
                    categoryEntity.setName(nameBuckets.get(0).getKeyAsString());
                }
                return categoryEntity;
            }).collect(Collectors.toList());
            responseVo.setCategories(categoryEntities);
        }

        // 获取规格参数嵌套聚合
        ParsedNested attrAgg = (ParsedNested)aggregationMap.get("attrAgg");
        // 获取嵌套聚合中的attrIdAgg子聚合
        ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrAgg.getAggregations().get("attrIdAgg");
        // 获取子聚合中的所有桶
        List<? extends Terms.Bucket> idAggBuckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(idAggBuckets)){
            // 每个桶转化成每个对应的SearchResponseAttrVo
            List<SearchResponseAttrVo> attrVos = idAggBuckets.stream().map(bucket -> {
                SearchResponseAttrVo responseAttrVo = new SearchResponseAttrVo();
                // 每一个桶中的key就是规格参数id
                responseAttrVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());

                // 获取桶中的子聚合
                Map<String, Aggregation> attrAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();

                // 获取规格参数名的子聚合
                ParsedStringTerms attrNameAgg = (ParsedStringTerms) attrAggregationMap.get("attrNameAgg");
                List<? extends Terms.Bucket> nameAggBuckets = attrNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameAggBuckets)){
                    responseAttrVo.setAttrName(nameAggBuckets.get(0).getKeyAsString());
                }

                // 获取规格参数值的子聚合
                ParsedStringTerms attrValueAgg = (ParsedStringTerms) attrAggregationMap.get("attrValueAgg");
                List<? extends Terms.Bucket> valueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(valueAggBuckets)){
                    List<String> attrValues = valueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                    responseAttrVo.setAttrValues(attrValues);
                }
                return responseAttrVo;
            }).collect(Collectors.toList());
            responseVo.setFilters(attrVos);
        }

        return responseVo;
    }

    private SearchSourceBuilder buildDSL(SearchParamVo paramVo){
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isBlank(keyword)){
            // TODO：打广告
            return sourceBuilder;
        }

        // 1.构建查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        // 1.1. 匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));

        // 1.2. 品牌过滤
        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }

        // 1.3. 分类过滤
        List<Long> categoryId = paramVo.getCategoryId();
        if (!CollectionUtils.isEmpty(categoryId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", categoryId));
        }

        // 1.4. 规格参数过滤
        // &props=4:6G-8G-12G&props=5:128G
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)){
            props.forEach(prop -> {
                // 把每个prop：4:6G-8G-12G 以冒号分割
                String[] attr = StringUtils.split(prop, ":");
                if (attr != null && attr.length == 2) {
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    // 分割后的第一位是attrId
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attr[0]));
                    // 分割后的第二位是：6G-8G-12G，再以-分割
                    String[] attrValues = StringUtils.split(attr[1], "-");
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", attrValues));
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));
                }
            });
        }

        // 1.5. 价格区间
        Double priceFrom = paramVo.getPriceFrom();
        Double priceTo = paramVo.getPriceTo();
        // 如果价格区间都为空，则什么都不做
        if (priceFrom != null || priceTo != null){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            // 如果priceFrom不为空，则gte查询
            if (priceFrom != null){
                rangeQuery.gte(priceFrom);
            }
            // 如果priceTo不为空，则lte查询
            if (priceTo != null){
                rangeQuery.lte(priceTo);
            }
            boolQueryBuilder.filter(rangeQuery);
        }

        // 1.6. 是否有货
        Boolean store = paramVo.getStore();
        if (store != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }

        // 2.构建排序: 0-得分排序 1-价格升序 2-价格降序排列 3-新品降序 4-销量降序
        Integer sort = paramVo.getSort();
        if (sort == null){
            sort = 0;
        }
        switch (sort) {
            case 1: sourceBuilder.sort("price", SortOrder.DESC); break;
            case 2: sourceBuilder.sort("price", SortOrder.ASC); break;
            case 3: sourceBuilder.sort("createTime", SortOrder.DESC); break;
            case 4: sourceBuilder.sort("sales", SortOrder.DESC); break;
            default:
                sourceBuilder.sort("_score", SortOrder.DESC);
                break;
        }

        // 3.构建分页
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);

        // 4.高亮
        sourceBuilder.highlighter(
                new HighlightBuilder()
                        .field("title")
                        .preTags("<font style='color:red;'>")
                        .postTags("</font>")
        );

        // 5.构建聚合
        // 5.1. 构建品牌聚合
        sourceBuilder.aggregation(
                AggregationBuilders.terms("brandIdAgg").field("brandId")
                        .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                        .subAggregation(AggregationBuilders.terms("logoAgg").field("logo"))
        );

        // 5.2. 构建分类聚合
        sourceBuilder.aggregation(
                AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                        .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName"))
        );

        // 5.3. 构建规格参数聚合
        sourceBuilder.aggregation(
                AggregationBuilders.nested("attrAgg", "searchAttrs")
                        .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                                .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                                .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue")))
        );

        // 6.添加结果集过滤，过滤出goods中所需要的5个字段
        sourceBuilder.fetchSource(new String[]{"skuId", "defaultImage", "price", "title", "subTitle"}, null);

        System.out.println(sourceBuilder.toString());
        return sourceBuilder;
    }
}
