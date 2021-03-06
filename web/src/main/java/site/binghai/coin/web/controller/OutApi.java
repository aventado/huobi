package site.binghai.coin.web.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.binghai.coin.common.entity.Kline;
import site.binghai.coin.common.entity.KlineTime;
import site.binghai.coin.common.response.Symbol;
import site.binghai.coin.common.utils.AnalysisUtils;
import site.binghai.coin.common.utils.CoinUtils;
import site.binghai.coin.common.utils.TimeFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by binghai on 2018/1/13.
 * data from 3rd website
 *
 * @ huobi
 */
//@RequestMapping("open")
//@RestController
//@CrossOrigin(origins = "*")
public class OutApi extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(OutApi.class);

    @RequestMapping("kline")
    public Object kline(@RequestParam Integer size, @RequestParam String coin, @RequestParam String qcoin, String callback) {
        List<Kline> rs = CoinUtils.getKlineList(new Symbol(coin, qcoin), KlineTime.MIN1, size);
        List<Kline> days = CoinUtils.getKlineList(new Symbol(coin, qcoin), KlineTime.DAY, 2);

        JSONObject result = new JSONObject();
        JSONObject info = new JSONObject();
        double currentPrice = rs.get(0).getClose();
        info.put("marketPanel_time", TimeFormat.format(rs.get(0).getId() * 1000));
        if (currentPrice >= days.get(1).getClose()) {
            info.put("marketPanel_riserate", "+" + String.format("%.2f", (currentPrice / days.get(1).getClose() - 1.0) * 100) + "%");
            info.put("marketPanel_fallrate", "");
        } else {
            info.put("marketPanel_riserate", "");
            info.put("marketPanel_fallrate", "-" + String.format("%.2f", (days.get(1).getClose() / currentPrice - 1.0) * 100) + "%");
        }
        info.put("marketPanel_cur_price", currentPrice);

        JSONArray arr = new JSONArray();
        rs.forEach(v -> {
            JSONArray item = new JSONArray();
            item.add(TimeFormat.format(v.getId() * 1000));
            item.add(v.getOpen());
            item.add(v.getClose());
            item.add(v.getLow());
            item.add(v.getHigh());
            item.add(v.getVol());

            arr.add(0, item);
        });

        result.put("info", info);
        result.put("list", arr);
        return callback == null ? arr : jqueryBack(callback, result);
    }

    @RequestMapping("symbols")
    public Object symbols(@RequestParam String qcoin, String callback) {
        List<Symbol> symbols = CoinUtils.allSymbols();
        qcoin.toLowerCase();
        JSONArray arr = new JSONArray();
        symbols.stream().filter(v -> v.getQuoteCurrency().equals(qcoin)).forEach(v -> {
            JSONObject item = new JSONObject();
            item.put("coin", v.getBaseCurrency().toUpperCase());
            item.put("baseCoin", v.getBaseCurrency().toLowerCase());
            arr.add(item);
        });

        return callback == null ? arr : jqueryBack(callback, arr);
    }

    /**
     * @param items BTC/USDT,QTUM/BTC...
     */
    @RequestMapping("relationShip")
    public Object relationShip(@RequestParam String items, @RequestParam Integer size, String callback) {
        String[] coins = items.split(",");
        JSONObject result = new JSONObject();
        List<String> xaxis = new ArrayList<>();
        List<JSONObject> seriesData = new ArrayList<>();


        List<Symbol> symbols = new ArrayList<>();
        for (String coin : coins) {
            symbols.add(new Symbol(coin.split("/")[0].toLowerCase(), coin.split("/")[1].toLowerCase()));
        }

        double avg = getAvgClose(symbols);

        for (Symbol coin : symbols) {
            List<Kline> ls = CoinUtils.getKlineList(coin, KlineTime.MIN15, size);
            double coefficient = avg / ls.get(0).getClose();

            if (xaxis.isEmpty()) {
                List<String> tmp = ls.stream().map(v -> TimeFormat.format(v.getId() * 1000)).collect(Collectors.toList());
                tmp.forEach(v -> xaxis.add(0, v));
            }

            JSONObject one = new JSONObject();
            one.put("name", coin.getBaseCurrency().toUpperCase() + "/" + coin.getQuoteCurrency().toUpperCase());
            one.put("type", "line");
            one.put("areaStyle", "{normal: {}}");
            one.put("data", ls.stream().map(v -> v.getClose() * coefficient).collect(Collectors.toList()));

            seriesData.add(0, one);
        }

        result.put("msg", getRelationShip(symbols,KlineTime.MIN15,size));
        result.put("items", coins);
        result.put("xaxis", xaxis);
        result.put("seriesData", seriesData);
        return callback == null ? result : jqueryBack(callback, result);
    }

    private String getRelationShip(List<Symbol> symbols,KlineTime time,int size) {
        if (symbols.size() == 1) {
            return "100%";
        }

        StringBuilder sb = new StringBuilder();
        List<Kline> base = CoinUtils.getKlineList(symbols.get(0),time,size);
        for (int i = 1; i < symbols.size(); i++) {
            sb.append(symbols.get(0).getSimpleName()+" ~ "+symbols.get(i).getSimpleName());
            List<Kline> cmp = CoinUtils.getKlineList(symbols.get(i),time,size);
            if(cmp.size() != base.size()){
                sb.append(": diff size ,");
            }else{
                double ratio = AnalysisUtils.get2CoinSimilarityRatio(base,cmp);
                sb.append(": "+ratio);
            }
        }

        return sb.toString();
    }

    /**
     * 计算均值
     */
    private double getAvgClose(List<Symbol> symbols) {
        double close = 0.0;
        for (Symbol symbol : symbols) {
            close += CoinUtils.getLastestKline(symbol).getClose();
        }
        return close / symbols.size();
    }
}
