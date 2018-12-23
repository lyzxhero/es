package com.lyzx;

import com.alibaba.fastjson.JSONObject;
import jdk.nashorn.internal.runtime.regexp.joni.Config;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;


public class EsDemo {

    private static TransportClient client;

    @Before
    public void initClient() throws UnknownHostException {
        //指定连接那个集群
        Settings setting = Settings.builder().put("cluster.name", "my-application").build();
        client = new PreBuiltTransportClient(setting);

        //指定集群的ip地址
        client
                .addTransportAddress(
                        new InetSocketTransportAddress(
                                InetAddress.getByName("slave1"),9300));

        System.out.println("init:客户端初始化成功");
    }


    @Test
    public void getNodes(){
        //获取集群的节点信息
        List<DiscoveryNode> nodes = client.filteredNodes();
        System.out.println("nodes.size():"+nodes.size());
        for(DiscoveryNode node : nodes){
            System.out.println("==============>"+node.getId());
        }
    }

    @Test
    public void createIndex(){
        //创建一个叫log的索引
        CreateIndexResponse response;
        try{
            response = client.admin().indices().prepareCreate("log2").get();
        }catch(ResourceAlreadyExistsException e){
//            e.printStackTrace();
            System.out.println("创建成功否:false,已经存在这个索引");
            return;
        }

        System.out.println("创建成功否:"+response.isAcknowledged());
    }

    @Test
    public void getIndex(){
        //获取所有创建的index
        GetIndexRequestBuilder indexs = client.admin().indices().prepareGetIndex();
        String[] indices = indexs.get().getIndices();
        System.out.println(Arrays.toString(indices));
    }


    @Test
    public void delIndex(){
        //删除索引 最后必须调用get() api  ,如果删除一个不存在index会抛出IndexNotFoundException异常

        DeleteIndexResponse response;

        try{
            response = client.admin().indices().prepareDelete("front-event").get();
        }catch(Exception e){
            //e.printStackTrace();
            System.out.println("没有这样的index");
            return;
        }

        System.out.println(response.isAcknowledged());
    }


    @Test
    public void existsIndex(){
        //检查某个index是否存在
        IndicesExistsResponse response = client.admin().indices().prepareExists("front-event").get();
        System.out.println(response.isExists());
    }


    @Test
    public void createType() throws IOException {
        String id = "112";
        String name = "武林风";
        String content = "入云龙董文飞";


//        Map<String,String>  data = new HashMap<String, String>();
////        JSONObject data = new JSONObject();
//        data.put("id",id);
//        data.put("name",name);
//        data.put("content",content);


        XContentBuilder data
            = XContentFactory
                .jsonBuilder()
                .startObject()
                .field("id", id)
                .field("name", name)
                .field("content", content)
                .endObject();


        /**
         * 在名字叫log的index下创建一个叫front-event的Type并且添加一个id为100的记录
         * 如果log下的front-event的存在就直接添加,不存在就创建一个
         *
         * .setSource( ... ) 有好几个重载方法
         * .setSource(String)
         * .setSource(Map<String,String>)
         * .setSource(XContentBuilder)
         */
        IndexResponse response = client.prepareIndex("log", "front-event", id)
                .setSource(data)

                .execute()
                .actionGet();

        System.out.println("index:"+response.getIndex());
        System.out.println("id:"+response.getId());
        System.out.println("sharedId:"+response.getShardId());
        System.out.println("type:"+response.getType());
        System.out.println("type:"+response.getResult());
    }


    @Test
    public void get(){
        String index = "log";
        String type = "front-event";
        String id = "100";

        GetResponse response = client.prepareGet(index, type, id).get();
        System.out.println(response.getSourceAsString());
    }


    @Test
    public void getWithMultipleIndex(){
        String index = "log";
        String type = "front-event";

        //多个ID的查找
        MultiGetResponse responses = client
                .prepareMultiGet()
                .add(index, type, "100","107","9000")
                .get();

        for(MultiGetItemResponse item :responses){
            GetResponse response = item.getResponse();
            if(response.isExists()){
                System.out.println(response.getSourceAsString());
            }else{
                System.out.println("id="+response.getId());
            }
        }
    }


    @Test
    public void update() throws Exception {
        UpdateRequest r = new UpdateRequest();
            r.index("log");
            r.type("front-event");
            r.id("100");
        //100 {"name":"冰与火之歌","id":"100","content":"这部剧还不错!nice"}
//        XContentBuilder doc = XContentFactory
//                .jsonBuilder()
//                .startObject()
//                .field("name", "冰与火之歌_modify by hero.li")
//                .field("comments", "这部剧好好看啊!")
//                .endObject();

        Map<String,String> doc = new HashMap<String, String>();
        doc.put("content","由Map来修改!");
        doc.put("addByMap","some text");

        r.doc(doc);
        UpdateResponse response = client.update(r).get();
        System.out.println("response.getIndex():"+response.getIndex());
        System.out.println("response.getType():"+response.getType());
        System.out.println("response.getId():"+response.getId());
        System.out.println("response.getGetResult():"+response.getGetResult());
        System.out.println("response.toString():"+response.toString());
    }


    @Test
    public void updateAndInsert() throws ExecutionException, InterruptedException {
        String id = "111";
        //插入或更新，如果没有id=109的项目就新增，否则就更新
        IndexRequest  insertRequest = new IndexRequest("log","front-event",id);
            Map<String,String> insertDoc = new HashMap<String, String>();
            insertDoc.put("id",id);
            insertDoc.put("myTitle","在上海!");
            insertDoc.put("name","new Name");
        insertRequest.source(insertDoc);

        UpdateRequest updateRequest = new UpdateRequest("log", "front-event", id);

        Map<String,String> updateDoc = new HashMap<String, String>(16);
        updateDoc.put("content","由Map来修改!");
        updateDoc.put("addByMap","some text");
        updateRequest.doc(updateDoc)
                     .upsert(insertRequest);

        UpdateResponse response = client.update(updateRequest).get();
        System.out.println(response.getIndex()+","+response.getType()+","+response.getId());
        System.out.println(response.getGetResult());
        System.out.println(response.getResult());

    }

    @Test
    public void search1(){

        SearchResponse response =
                client.prepareSearch("log")
                .setTypes("front-event")
                .setQuery(QueryBuilders.matchAllQuery())
                .get();

        SearchHits hits = response.getHits();
        for (SearchHit hit : hits) {
            System.out.println(hit.getId()+"    "+hit.getSourceAsString());
        }
        System.out.println(response);
    }

    @Test
    public void search2(){

        SearchResponse response =
            client.prepareSearch("log")
            .setTypes("front-event")
            .setQuery(QueryBuilders.queryStringQuery("龙"))
            .get();

        SearchHits hits = response.getHits();
        System.out.println("hits.totalHits()="+hits.totalHits());


        for(SearchHit hit : hits) {
            System.out.println(hit.getId()+"     "+hit.getSourceAsString());
        }
    }

    @Test
    public void searchWild(){

        SearchResponse result
                = client.prepareSearch("log")
                .setTypes("front-event")
                //在content字段查询有nice的记录
                .setQuery(QueryBuilders.wildcardQuery("content","*nice*"))
                .get();

        SearchHits hits = result.getHits();
        System.out.println("hits.totalHits()="+hits.totalHits());
        for (SearchHit hit : hits) {
            System.out.println(hit.getId()+"   "+hit.getSourceAsString());
        }



    }

    @After
    public void clearClient(){
        client.close();
        System.out.println("==>客户端关闭成功");
    }
}
