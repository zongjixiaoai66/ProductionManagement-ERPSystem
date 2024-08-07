
package com.controller;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.text.SimpleDateFormat;
import com.alibaba.fastjson.JSONObject;
import java.util.*;
import org.springframework.beans.BeanUtils;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import com.service.TokenService;
import com.utils.*;
import java.lang.reflect.InvocationTargetException;

import com.service.DictionaryService;
import org.apache.commons.lang3.StringUtils;
import com.annotation.IgnoreAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.entity.*;
import com.entity.view.*;
import com.service.*;
import com.utils.PageUtils;
import com.utils.R;
import com.alibaba.fastjson.*;

/**
 * 产品信息
 * 后端接口
 * @author
 * @email
*/
@RestController
@Controller
@RequestMapping("/chanpin")
public class ChanpinController {
    private static final Logger logger = LoggerFactory.getLogger(ChanpinController.class);

    @Autowired
    private ChanpinService chanpinService;


    @Autowired
    private TokenService tokenService;
    @Autowired
    private DictionaryService dictionaryService;

    //级联表service

    @Autowired
    private YonghuService yonghuService;
    @Autowired
    private WeixiuyuanService weixiuyuanService;


    /**
    * 后端列表
    */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("page方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");
        else if("员工".equals(role))
            params.put("yonghuId",request.getSession().getAttribute("userId"));
        else if("维修员".equals(role))
            params.put("weixiuyuanId",request.getSession().getAttribute("userId"));
        if(params.get("orderBy")==null || params.get("orderBy")==""){
            params.put("orderBy","id");
        }
        PageUtils page = chanpinService.queryPage(params);

        //字典表数据转换
        List<ChanpinView> list =(List<ChanpinView>)page.getList();
        for(ChanpinView c:list){
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(c, request);
        }
        return R.ok().put("data", page);
    }

    /**
    * 后端详情
    */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id, HttpServletRequest request){
        logger.debug("info方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        ChanpinEntity chanpin = chanpinService.selectById(id);
        if(chanpin !=null){
            //entity转view
            ChanpinView view = new ChanpinView();
            BeanUtils.copyProperties( chanpin , view );//把实体数据重构到view中

            //修改对应字典表字段
            dictionaryService.dictionaryConvert(view, request);
            return R.ok().put("data", view);
        }else {
            return R.error(511,"查不到数据");
        }

    }

    /**
    * 后端保存
    */
    @RequestMapping("/save")
    public R save(@RequestBody ChanpinEntity chanpin, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,chanpin:{}",this.getClass().getName(),chanpin.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(StringUtil.isEmpty(role))
            return R.error(511,"权限为空");

        Wrapper<ChanpinEntity> queryWrapper = new EntityWrapper<ChanpinEntity>()
            .eq("chanpin_uuid_unmber", chanpin.getChanpinUuidUnmber())
            .eq("chanpin_name", chanpin.getChanpinName())
            .eq("chanpin_types", chanpin.getChanpinTypes())
            .eq("shangxia_types", chanpin.getShangxiaTypes())
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        ChanpinEntity chanpinEntity = chanpinService.selectOne(queryWrapper);
        if(chanpinEntity==null){
            chanpin.setShangxiaTypes(1);
            chanpin.setCreateTime(new Date());
            chanpinService.insert(chanpin);
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 后端修改
    */
    @RequestMapping("/update")
    public R update(@RequestBody ChanpinEntity chanpin, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,chanpin:{}",this.getClass().getName(),chanpin.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
//        if(StringUtil.isEmpty(role))
//            return R.error(511,"权限为空");
        //根据字段查询是否有相同数据
        Wrapper<ChanpinEntity> queryWrapper = new EntityWrapper<ChanpinEntity>()
            .notIn("id",chanpin.getId())
            .andNew()
            .eq("chanpin_uuid_unmber", chanpin.getChanpinUuidUnmber())
            .eq("chanpin_name", chanpin.getChanpinName())
            .eq("chanpin_types", chanpin.getChanpinTypes())
            .eq("shangxia_types", chanpin.getShangxiaTypes())
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        ChanpinEntity chanpinEntity = chanpinService.selectOne(queryWrapper);
        if(chanpinEntity==null){
            chanpinService.updateById(chanpin);//根据id更新
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 删除
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids){
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        chanpinService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }


    /**
     * 批量上传
     */
    @RequestMapping("/batchInsert")
    public R save( String fileName){
        logger.debug("batchInsert方法:,,Controller:{},,fileName:{}",this.getClass().getName(),fileName);
        try {
            List<ChanpinEntity> chanpinList = new ArrayList<>();//上传的东西
            Map<String, List<String>> seachFields= new HashMap<>();//要查询的字段
            Date date = new Date();
            int lastIndexOf = fileName.lastIndexOf(".");
            if(lastIndexOf == -1){
                return R.error(511,"该文件没有后缀");
            }else{
                String suffix = fileName.substring(lastIndexOf);
                if(!".xls".equals(suffix)){
                    return R.error(511,"只支持后缀为xls的excel文件");
                }else{
                    URL resource = this.getClass().getClassLoader().getResource("static/upload/" + fileName);//获取文件路径
                    File file = new File(resource.getFile());
                    if(!file.exists()){
                        return R.error(511,"找不到上传文件，请联系管理员");
                    }else{
                        List<List<String>> dataList = PoiUtil.poiImport(file.getPath());//读取xls文件
                        dataList.remove(0);//删除第一行，因为第一行是提示
                        for(List<String> data:dataList){
                            //循环
                            ChanpinEntity chanpinEntity = new ChanpinEntity();
//                            chanpinEntity.setChanpinUuidUnmber(data.get(0));                    //产品编号 要改的
//                            chanpinEntity.setChanpinName(data.get(0));                    //产品名称 要改的
//                            chanpinEntity.setChanpinTypes(Integer.valueOf(data.get(0)));   //产品类型 要改的
//                            chanpinEntity.setShangxiaTypes(Integer.valueOf(data.get(0)));   //是否上架 要改的
//                            chanpinEntity.setChanpinContent("");//照片
//                            chanpinEntity.setCreateTime(date);//时间
                            chanpinList.add(chanpinEntity);


                            //把要查询是否重复的字段放入map中
                        }

                        //查询是否重复
                        chanpinService.insertBatch(chanpinList);
                        return R.ok();
                    }
                }
            }
        }catch (Exception e){
            return R.error(511,"批量插入数据异常，请联系管理员");
        }
    }






}
