package id.flutter.flutter_background_service;

import io.flutter.plugin.common.EventChannel;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.util.Log;
import android.content.Context;
import android.os.Bundle;
import android.telephony.SmsMessage;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.time.LocalDateTime; // Import the LocalDateTime class
import java.time.format.DateTimeFormatter; 



public class SmsReceiver extends BroadcastReceiver{
    public static EventChannel.EventSink eventSink;
String TAG = "SmsReceiver";

    public String getAmount(String data){
        // pattern - rs. **,***.**
        String pattern1 = "(?i)(?:(?:RS|INR|MRP)\\.?\\s?)(\\d+(:?\\,\\d+)?(\\,\\d+)?(\\.\\d{1,2})?)";
        Pattern regex1= Pattern.compile(pattern1,Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = regex1.matcher(data);
        if (matcher1.find()) {
            try {
                String a= matcher1.group(0);
                a = a.toLowerCase();
                a = a.replace("inr.", "");
                a = a.replace("rs.", "");
                a = a.replace("mrp.", "");
                a = a.replace("inr", "");
                a = a.replace("rs", "");
                a = a.replace("mrp", "");
                a = a.replace(" ", "");
                a = a.replace(",", "");
                return a.trim();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } 
        return "";

    }

    public String getAccountnumber(String data){
        String pattern1 = "[0-9]*[Xx]*[0-9]*[Xx]+[0-9]{3,}";
        Pattern regex1= Pattern.compile(pattern1,Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = regex1.matcher(data);
        if (matcher1.find()) {
            try {
                String a= matcher1.group(0);
                return a.trim();
                } catch (Exception e) {
                e.printStackTrace();
            }
        } 
        return "";
    }
    public boolean isSenderValid(String sender){
        return (sender.contains("08586980869")
                || sender.contains("085869")
                || sender.contains("ICICIB")
                || sender.contains("HDFCBK")
                || sender.contains("SBIINB")
                || sender.contains("SBMSMS")
                || sender.contains("SCISMS")
                || sender.contains("CBSSBI")
                || sender.contains("SBIPSG")
                || sender.contains("SBIUPI")
                || sender.contains("SBICRD")
                || sender.contains("ATMSBI")
                || sender.contains("QPMYAMEX")
                || sender.contains("IDFCFB")
                || sender.contains("UCOBNK")
                || sender.contains("CANBNK")
                || sender.contains("BOIIND")
                || sender.contains("AXISBK")
                || sender.contains("PAYTMB")
                || sender.contains("UnionB")
                || sender.contains("INDBNK")
                || sender.contains("KOTAKB")
                || sender.contains("CENTBK")
                || sender.contains("SCBANK")
                || sender.contains("PNBSMS")
                || sender.contains("DOPBNK")
                || sender.contains("YESBNK")
                || sender.contains("IDBIBK")
                || sender.contains("ALBANK")
                || sender.contains("CITIBK")
                || sender.contains("ANDBNK")
                || sender.contains("BOBTXN")
                || sender.contains("IOBCHN")
                || sender.contains("MAHABK")
                || sender.contains("OBCBNK")
                || sender.contains("RBLBNK")
                || sender.contains("RBLCRD")
                || sender.contains("SPRCRD")
                || sender.contains("HSBCBK")
                || sender.contains("HSBCIN")
                || sender.contains("INDUSB"));
    }

    public Map<String, String> parseValues(Map<String, String> msg){
        String pattern1 = "(?=.*[Aa]ccount.*|.*[Aa]/?[Cc].*|.*[Aa][Cc][Cc][Tt].*|.*[Cc][Aa][Rr][Dd].*)(?=.*[Cc]redit.*|.*[Dd]ebit.*)(?=.*[Ii][Nn][Rr].*|.*[Rr][Ss].*)";
        Pattern regex1= Pattern.compile(pattern1,Pattern.CASE_INSENSITIVE);
        String data = msg.getOrDefault("body","");
        String lbody = data.toLowerCase();
        Matcher matcher1 = regex1.matcher(data);
        boolean isMsg = matcher1.find();
        Log.d(TAG,"Matched :: "+isMsg);
        if(isMsg){
            if(!this.isSenderValid(msg.getOrDefault("sender",""))){
                if (!lbody.contains("stmt") && !lbody.contains("otp") && !lbody.contains("minimum")
                                && !lbody.contains("importance")
                                && !lbody.contains("request")
                                && !lbody.contains("limit")
                                && !lbody.contains("convert")
                                && !lbody.contains("emi")
                                && !lbody.contains("avoid paying")
                                && !lbody.contains("autopay")
                                && !lbody.contains("e-statement")
                                && !lbody.contains("funds are blocked")
                                && !lbody.contains("smartpay")
                                && !lbody.contains("we are pleased to inform that")
                                && !lbody.contains("has been opened")) {
                            // found out debit and credit
                            if (lbody.contains("withdrawn")
                                || lbody.contains("debited")
                                || lbody.contains("spent")
                                || lbody.contains("using")
                                || lbody.contains("paying")
                                || lbody.contains("payment")
                                || lbody.contains("deducted")
                                || lbody.contains("debited")
                                || lbody.contains("purchase")
                                || lbody.contains("dr")
                                && !lbody.contains("otp")
                                || lbody.contains("txn")
                                || lbody.contains("transfer")
                                && !lbody.contains("we are pleased to inform that")
                                && !lbody.contains("has been opened")
                            ) {
                                msg.put("transactionType","debited");
                                msg.put("amount",this.getAmount(data));
                            }
                            else if(lbody.contains("credited")
                                || lbody.contains("cr")
                                || lbody.contains("deposited")
                                || lbody.contains("deposit")
                                || lbody.contains("received")
                                && !lbody.contains("otp")
                                && !lbody.contains("emi")){
                                    msg.put("transactionType","credited");
                                    msg.put("amount",this.getAmount(data));
                                }
                }
            }
        }
        return msg;
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        
        Log.d(TAG,"Received new message");
        
        Log.d(TAG,intent.getAction());
        if(intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")){
            Bundle bundle = intent.getExtras();
            SmsMessage[] msgs = null;
            LocalDateTime myDateObj = LocalDateTime.now();
            DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

            String formattedDate = myDateObj.format(myFormatObj);
            

            ArrayList<Map<String, String>> messages = new ArrayList<Map<String, String>>();
            
            Log.i(TAG,"Received new message");
            if (bundle != null){
                //---retrieve the SMS message received---
                
                try{
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    for(int i=0; i<pdus.length; i++){
                        Map<String, String> msg = new HashMap<String, String>();
                        SmsMessage m = SmsMessage.createFromPdu((byte[])pdus[i]);
                        msg.put("sender",m.getOriginatingAddress());
                        msg.put("body",m.getMessageBody());
                        msg.put("transactionType","");
                        msg.put("amount","");
                        msg.put("payeeName","");
                        msg.put("date",formattedDate);
                        msg = this.parseValues(msg);
                        if(!msg.getOrDefault("amount","").isEmpty() && !msg.getOrDefault("transactionType","").isEmpty()){
                            msg.put("account_number",this.getAccountnumber(msg.getOrDefault("body","")));
                            messages.add(msg);
                            Log.d(TAG,"Message :: "+i);
                            for (Map.Entry<String, String> entry : msg.entrySet()) {
                                    Log.i(TAG,entry.getKey()+" : "+entry.getValue());
                            }
                        }
                        
                    }
                }catch(Exception e){
//                            Log.d("Exception caught",e.getMessage());
                    Log.e(TAG,"Exception occured "+e.getMessage());
                }
                Log.d(TAG,"Total :: "+messages.size());
                try{
                if(messages.size() > 0){
                eventSink.success(messages);
                }
                }catch(Exception exp){
                    Log.d(TAG,"Exception occured while sending stream event :: "+exp);
                }
            }
        }
    }
}