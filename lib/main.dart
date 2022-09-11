import 'dart:async';
import 'dart:developer';
import 'dart:ui';

import 'package:expense_tracker/utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:flutter_background_service_android/flutter_background_service_android.dart';
import 'package:path/path.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';

String serviceStatus = "Start";

const _channel = EventChannel('com.appinnovations.expense_tracker/sms_helper');

var totalDebited = 0.0;
var totalDebitedStringMin = "";
var totalCredited = 0.0;
var totalCreditedStringMin = "";

bool showFullAmount = false;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeService();
  runApp(const MyApp());
}

Future<void> initializeService() async {
  final service = FlutterBackgroundService();
  log("Configuring");
  await service.configure(
    androidConfiguration: AndroidConfiguration(
      onStart: onStart,

      // auto start service
      autoStart: false,
      isForegroundMode: true,
      foregroundServiceNotificationTitle: "",
      foregroundServiceNotificationContent: "",
    ),
    iosConfiguration: IosConfiguration(
      autoStart: false,
      onForeground: onStart,
      onBackground: onIosBackground,
    ),
  );
  final fservice = FlutterBackgroundService();
  var isRunning = await fservice.isRunning();
  serviceStatus = isRunning ? "Stop" : "Start";
  log("IS Running :: $isRunning");
  // Get a location using getDatabasesPath
  var databasesPath = await getDatabasesPath();
  String path = join(databasesPath, 'data.db');
  // open the database
  Database database = await openDatabase(path, version: 1,
      onCreate: (Database db, int version) async {
    // When creating the db, create the table
    await db.execute(
        'CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT, transactionType TEXT, amount REAL,date TEXT, account_number TEXT, payeeName Text)');
  });
  log("Created DB instance");
  var prefs = await SharedPreferences.getInstance();
  if (prefs.containsKey("showFullAmount")) {
    var v = prefs.getBool("showFullAmount");
    showFullAmount = v!;
  }
  log("Show full amount? == $showFullAmount");
}

// to ensure this is executed
// run app from xcode, then from xcode menu, select Simulate Background Fetch
bool onIosBackground(ServiceInstance service) {
  WidgetsFlutterBinding.ensureInitialized();

  return true;
}

void onStart(ServiceInstance service) async {
  DartPluginRegistrant.ensureInitialized();
  var prefs = await SharedPreferences.getInstance();
  if (prefs.containsKey("showFullAmount")) {
    var v = prefs.getBool("showFullAmount");
    showFullAmount = v!;
  } else {
    await prefs.setBool("showFullAmount", showFullAmount);
  }

  var databasesPath = await getDatabasesPath();
  String path = join(databasesPath, 'data.db');
  // open the database
  Database database = await openDatabase(path, version: 1);

  if (service is AndroidServiceInstance) {
    service.on('updateNotificationIfPresent').listen((event) {
      showFullAmount = event!["showFullAmount"]!;
      log("Updating notification :: $showFullAmount");
      service.setForegroundNotificationInfo(
        title: "Tracking your expenses",
        content: "",
        money_in: showFullAmount
            ? totalCredited.toStringAsFixed(2)
            : totalCreditedStringMin,
        money_out: showFullAmount
            ? totalDebited.toStringAsFixed(2)
            : totalDebitedStringMin,
      );
    });
    service.on('setAsForeground').listen((event) async {
      service.setAsForegroundService();
      log("Foreground...");
      List<Map<String, dynamic>> debRecords = await database.rawQuery(
          'select sum(amount) as amount from "transactions" where "transactionType"="debited"');
      List<Map<String, dynamic>> credRecords = await database.rawQuery(
          'select sum(amount) as amount from "transactions" where "transactionType"="credited"');

      log("$debRecords");
      log("$credRecords");

      for (var i = 0; i < debRecords.length; i++) {
        if (debRecords[i]["amount"] != null) {
          totalDebited = totalDebited + debRecords[i]["amount"];
        }
      }
      for (var i = 0; i < credRecords.length; i++) {
        if (credRecords[i]["amount"] != null) {
          totalCredited = totalCredited + credRecords[i]["amount"];
        }
      }
      totalDebitedStringMin = getAmountString(amount: totalDebited);
      totalCreditedStringMin = getAmountString(amount: totalCredited);

      service.setForegroundNotificationInfo(
        title: "Tracking your expenses",
        content: "",
        money_in: showFullAmount
            ? totalCredited.toStringAsFixed(2)
            : totalCreditedStringMin,
        money_out: showFullAmount
            ? totalDebited.toStringAsFixed(2)
            : totalDebitedStringMin,
      );
    });

    service.on('setAsBackground').listen((event) {
      service.setAsBackgroundService();
    });
  }

  service.on('stopService').listen((event) {
    service.stopSelf();
  });

  _channel.receiveBroadcastStream().listen((event) async {
    log("Message Received :: $event");
    var batch = database.batch();
    for (var i = 0; i < event.length; i++) {
      try {
        var amount = double.tryParse(event[i]['amount']);
        if (amount != null) {
          if (event[i]['transactionType'] == "debited") {
            totalDebited = totalDebited + amount;
          } else if (event[i]['transactionType'] == "credited") {
            totalCredited = totalCredited + amount;
          }
        }
        batch.insert('transactions', {
          'sender': event[i]['sender'],
          'transactionType': event[i]['transactionType'],
          'account_number': event[i]['account_number'],
          'amount': amount,
          'payeeName': event[i]['payeeName']
        });
      } catch (e) {
        log("Exception occured while saving :: $e");
      }
    }
    totalDebitedStringMin = getAmountString(amount: totalDebited);
    totalCreditedStringMin = getAmountString(amount: totalCredited);

    if (service is AndroidServiceInstance) {
      service.setForegroundNotificationInfo(
        title: "Tracking your expenses",
        content: "",
        money_in: showFullAmount
            ? totalCredited.toStringAsFixed(2)
            : totalCreditedStringMin,
        money_out: showFullAmount
            ? totalDebited.toStringAsFixed(2)
            : totalDebitedStringMin,
      );
    }
    await batch.commit(noResult: true);
  });

  if (service is AndroidServiceInstance) {
    service.setForegroundNotificationInfo(
      title: "Tracking your expenses",
      content: "",
      money_in: "0",
      money_out: "0",
    );
  }
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  void initState() {
    super.initState();
  }

  final Future<SharedPreferences> _prefs = SharedPreferences.getInstance();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Expense Tracker'),
        ),
        body: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          mainAxisSize: MainAxisSize.max,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Text("Show Full Amount"),
                const SizedBox(
                  width: 5,
                ),
                Checkbox(
                    value: showFullAmount,
                    onChanged: (value) async {
                      log("Changed :: $value");

                      showFullAmount = value!;
                      setState(() {});
                      var prefs = await SharedPreferences.getInstance();
                      await prefs.setBool("showFullAmount", value);
                      var ser = FlutterBackgroundService();
                      var isRunning = await ser.isRunning();
                      if (isRunning) {
                        ser.invoke("updateNotificationIfPresent",
                            {"showFullAmount": value});
                      }
                    }),
              ],
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton(
                  child: Text(serviceStatus),
                  onPressed: () async {
                    final service = FlutterBackgroundService();
                    var isRunning = await service.isRunning();
                    if (isRunning) {
                      service.invoke("stopService");
                      log("Stopped");
                    } else {
                      var status = await Permission.sms.status;
                      if (status.isDenied) {
                        status = await Permission.sms.request();
                      }
                      if (status.isPermanentlyDenied) {
                        await openAppSettings();
                      }
                      status = await Permission.sms.status;
                      if (status.isGranted) {
                        setState(() {
                          serviceStatus = "Starting...";
                        });
                        await service.startService().then((value) async {
                          if (value) {
                            await Future.delayed(
                              const Duration(seconds: 2),
                              () => FlutterBackgroundService()
                                  .invoke("setAsForeground"),
                            );
                          } else {
                            isRunning = value;
                          }
                          log("Started service");
                        });
                      } else {
                        // TODO :: Dialog to let user know about permission
                        log("SMS permission is not available");
                      }
                    }

                    if (!isRunning) {
                      serviceStatus = 'Stop';
                    } else {
                      serviceStatus = 'Start';
                    }
                    setState(() {});
                  },
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
