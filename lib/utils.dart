getAmountString({var amount = 0.0}) {
  var amountStringMin = "";
  if (amount < 1000) {
    amountStringMin = "$amount";
  } else if (amount < 1e6) {
    var v = (amount / 1000).toStringAsFixed(2);
    amountStringMin = "${v}K";
  } else if (amount < 1e9) {
    var v = (amount / 1e6).toStringAsFixed(2);
    amountStringMin = "${v}M";
  } else if (amount < 1e12) {
    var v = (amount / 1e9).toStringAsFixed(2);
    amountStringMin = "${v}B";
  }
  return amountStringMin;
}
