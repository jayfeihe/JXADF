package com.jxtech.integration.services;

import com.jxtech.common.JxActionSupport;
import com.jxtech.integration.jsonvo.IntergrationVo;
import com.jxtech.integration.jsonvo.JboVo;
import com.jxtech.jbo.App;
import com.jxtech.jbo.JboIFace;
import com.jxtech.jbo.JboSetIFace;
import com.jxtech.jbo.JboValue;
import com.jxtech.jbo.auth.JxSession;
import com.jxtech.jbo.base.JxAttribute;
import com.jxtech.jbo.util.JboUtil;
import com.jxtech.jbo.util.JxException;
import com.jxtech.util.StrUtil;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 平台集成服务servlet
 * Created by cxm on 15/4/20
 */

public class IntergrationAction extends JxActionSupport {
    private static final long serialVersionUID = 1L;

    private static Logger LOG = LoggerFactory.getLogger(IntergrationAction.class);
    private static Map<String, Class> CLASS_MAP = new HashMap<String, Class>();

    static {
        CLASS_MAP.put("_jbos", JboVo.class);
        CLASS_MAP.put("_datas", HashMap.class);
        CLASS_MAP.put("_childrens", JboVo.class);
    }

    private String intergrationData;

    public String execute() throws Exception {
        if (!StrUtil.isNull(intergrationData)) {
            JSONObject jsonObject = JSONObject.fromObject(intergrationData);

            try {
                IntergrationVo ivo = (IntergrationVo) JSONObject.toBean(jsonObject, IntergrationVo.class, CLASS_MAP);
                if (null != ivo) {
                    //首先获取数据中的jboname，如果jboname为空则根据appnametype获取对应的app，然后获取app的jboname
                    String appNameType = ivo.get_appNameType();
                    String jboName = ivo.get_jboname();

                    if (StrUtil.isNull(jboName)) {
                        if (!StrUtil.isNull(appNameType)) {
                            App app = JxSession.getApp(appNameType);
                            if (null != app) {
                                jboName = app.getJboset().getAppname();
                            }
                        }
                    }

                    if (!StrUtil.isNull(jboName)) {
                        JboSetIFace jboSet = JboUtil.getJboSet(jboName);
                        jboSet.queryAll();

                        for (JboVo jboVo : ivo.get_jbos()) {
                            JboIFace jbo = convertJboFromVo(jboVo, jboSet);
                            if (null != jbo) {
                                if (null != jboVo.get_childrens()) {
                                    for (JboVo childJboVo : jboVo.get_childrens()) {
                                        handleChildJboVo(jbo, childJboVo);
                                    }
                                }
                            }
                        }
                        jboSet.commit();
                    }
                }
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }

        }

        return super.execute();
    }

    private JboIFace convertJboFromVo(JboVo jboVo, JboSetIFace jboSet) throws JxException {
        JboIFace jbo = null;
        if (null != jboVo) {
            if (null != jboSet) {
                String action = jboVo.get_action();
                if (!StrUtil.isNull(action)) {
                    if ("C".equalsIgnoreCase(action)) {
                        jbo = jboSet.add();
                        jboVo.set_uid(jbo.getUidValue());
                        jbo.getData().putAll(jboVo.get_datas());
                    } else if (!StrUtil.isNull(jboVo.get_uid())) {
                        jbo = jboSet.getJboOfUid(jboVo.get_uid());
                        if ("U".equalsIgnoreCase(action)) {
                            modifyJboValue(jbo, jboVo);
                            jbo.setModify(true);
                        } else if ("D".equalsIgnoreCase(action)) {
                            jbo.delete();
                        }
                    }
                }
            }


        }
        return jbo;
    }

    private JboIFace convertChildJboFormVo(JboIFace pJbo, JboVo childJboVo) throws JxException {
        JboIFace childJbo = null;

        if (null != pJbo && null != childJboVo) {
            String relationshipname = childJboVo.get_relationshipname();
            String childJboAction = childJboVo.get_action();

            if (!StrUtil.isNull(relationshipname) && !StrUtil.isNull(childJboAction)) {

                JboSetIFace jboSet = pJbo.getChildrenJboSet(relationshipname);
                if (null == jboSet) {
                    jboSet = pJbo.getRelationJboSet(relationshipname);
                    pJbo.getChildrenJboSet(relationshipname).setJbolist(jboSet.queryAll());
                }

                //当父JBO为新增的时候，子jbo只能是新增的数据啊。没有其他操作
                if (pJbo.isToBeAdd()) {
                    if ("C".equalsIgnoreCase(childJboAction)) {
                        childJbo = jboSet.add();
                        childJbo.getData().putAll(childJboVo.get_datas());
                        childJboVo.set_uid(childJbo.getUidValue());
                    } else {
                        //nothing here...
                    }
                } else if (pJbo.isToBeDel()) {
                    childJbo = null;
                } else if (pJbo.isModify()) {
                    if ("C".equalsIgnoreCase(childJboAction)) {
                        childJbo = jboSet.add();
                        childJbo.getData().putAll(childJboVo.get_datas());
                        childJboVo.set_uid(childJbo.getUidValue());
                    } else if ("R".equalsIgnoreCase(childJboAction)) {

                    } else if ("U".equalsIgnoreCase(childJboAction)) {
                        childJbo = getJboInSet(jboSet, childJboVo.get_uid());
                        if (null != childJbo) {
                            //childJbo.getData().putAll(childJboVo.getDatas());
                            modifyJboValue(childJbo, childJboVo);
                            childJbo.setModify(true);
                        }
                    } else if ("D".equalsIgnoreCase(childJboAction)) {
                        childJbo = getJboInSet(jboSet, childJboVo.get_uid());
                        if (null != childJbo) {
                            childJbo.delete();
                        }
                    }
                } else {
                    //读取数据了
                }
            }
        }
        return childJbo;
    }

    /**
     * 处理单个jbovo对象，执行crud操作
     *
     * @param pJbo       jboname或者relationshipname
     * @param childJboVo jbovo对象
     * @throws JxException
     */
    private void handleChildJboVo(JboIFace pJbo, JboVo childJboVo) throws JxException {
        JboIFace childJbo = convertChildJboFormVo(pJbo, childJboVo);
        if (null != childJbo && null != childJboVo.get_childrens()) {
            for (JboVo ccJbovo : childJboVo.get_childrens()) {
                handleChildJboVo(childJbo, ccJbovo);
            }
        }
    }


    private void modifyJboValue(JboIFace jbo, JboVo jboVo) throws JxException {
        JxAttribute attr;
        Map<String, Object> voDatas = jboVo.get_datas();
        Iterator<String> keyIte = voDatas.keySet().iterator();

        while (keyIte.hasNext()) {
            String key = keyIte.next();
            attr = jbo.getJxAttribute(key);
            if (null != attr) {
                String attrValue = jbo.getString(key);
                if (null != attrValue) {
                    if (StrUtil.isNull(attrValue) || !attrValue.equals(voDatas.get(key))) {
                        jbo.setObject(key, voDatas.get(key));
                    }
                } else {
                    jbo.setObject(key, voDatas.get(key));
                }
            }
        }
    }

    private JboIFace getJboInSet(JboSetIFace jboSet, String uid) throws JxException {
        JboIFace jbo = null;
        if (null != jboSet && !StrUtil.isNull(uid)) {
            if (jboSet.getJbolist().isEmpty()) {
                jboSet.queryAll();
            }

            for (JboIFace qJbo : jboSet.getJbolist()) {
                if (null != qJbo) {
                    if (qJbo.getUidValue().equals(uid)) {
                        jbo = qJbo;
                        break;
                    }
                }
            }
        }

        return jbo;
    }


    public String getIntergrationData() {
        return intergrationData;
    }

    public void setIntergrationData(String intergrationData) {
        this.intergrationData = intergrationData;
    }
}