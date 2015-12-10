var fiReportApp = angular.module('fiReportApp', []);
fiReportApp.filter("sanitize", ['$sce', function($sce) {
  return function(htmlCode) { return $sce.trustAsHtml(htmlCode); }
}]);
fiReportApp.controller('fiCtrl', function ($scope) {
	var colors = d3.scale.category20c();
	$scope.getArray = function(n) { return new Array(n); };
	$scope.aFi = {
		marketNeeds: {},
		socialBenefits: [],
		enablers: [],
		totalSurveys: fi.data.total,
		trl: fi.questions.trl,
		revenueDivision: "",
		
		organisationType: fi.getAnswersList('1_6', 'A'),
		businessModel: fi.verboseFromJSON('3', '1'),
		marketSectors: fi.verboseFromJSON('3', '3'),
		marketChannel: fi.verboseFromJSON('3', '4'),
		data3_5: fi.getQ3_5text()
	};

	function linkEnablers(str) {
		var result = str;
		$.each(model.enablers, function(i, v) { if (v.link != "") result = result.replace(v.enabler, '<a target="_blank" href="' + v.link + '">' + v.enabler + '</a>'); });
		return result
	}
	
	$.each(fi.questions, function(i, v) { if ( v && (v.text != "") ) { $scope.aFi['d' + v.id] = v.text; } });
	$scope.aFi['d1_12'] = linkEnablers($scope.aFi['d1_12']);
	$.each(["a", "b", "c", "d"], function(i, v) {
		if (fi.questions['Q1_18' + v]) { $scope.aFi.enablers.push({category: model.Q1_18text[v], list: linkEnablers(fi.questions['Q1_18' + v].text)}); }
	});
	var t3_2 = [];
	$.each(["a", "b", "c"], function(i, v) {
		input = fi.questions['Q3_2' + v];
		if (input && parseInt(input.value) > 0) { t3_2.push(model.revenueDivision[v] + ": " + input.value + "%"); }
	});
	$scope.aFi.revenueDivision = t3_2.join(", ");
	$.each(model.speedometers, function(i, v) {
		m = model.max[v];
		$scope.aFi[v] = { max: m,
			score: fi.scores[v],
			average: fi.averages.values[v].average,
			ranking: model.ranking[fi.results[v.toUpperCase() + '_GRAPH_SLOT']],
			percentOfWorse: 100 - Math.round(fi.results[v.toUpperCase() + "_R"]),
			interpretation: model.interpretation[model.slots[Math.round(fi.results[v.toUpperCase() + '_GRAPH_SLOT'])] + model.slots[fi.averages.values['innovation'].slot]].replace(new RegExp('\\*\\*\\*', 'g'), v),
			bottomHalf: ( (fi.scores[v] <= m/2) ? [1] : [])
			// model.slots[fi.averages.values[v].slot] + 
		};
	});
	
	var t4_3 = []; //  Revenue Growth
	$.each(["3a", "3b", "3c", "3d"], function(i, v) { if (fi.questions['Q4_' + v]) { t4_3.push(fi.questions['Q4_' + v].value + '%'); } });
	$scope.aFi.revenueGrowth = t4_3.join(", ");
	
	// Market Needs
	$.each(fi.questions.Q3_3.value.split(','), function(i, v) {
		if (model.marketNeedsTop5[v]) {
		$scope.aFi.marketNeeds[v] = {
			sector: model.s3.q3[v],
			score: (fi.results["MARKET_NEEDS_BUSINESS_" + v] - 0.005).toFixed(2),
			scores: fi.getMarketNeedsStarScores(v),
			top5: fi.getVerboseList(model.marketNeedsTop5[v], model.s5A.q1),
			hint: model.graphSlotText[parseInt(fi.results["MARKET_NEEDS_BUSINESS_" + v + "_GRAPH_SLOT"])]
		}; }
	});
	
	// Social Benefits
	$.each(model.s6A.q1, function(i, v) { $scope.aFi.socialBenefits.push({ key: i, name: v }); });
});

// <i class="fa fa-star-o" ng-repeat="em in getArray(score.empty)"></i>
// c. Where do I fit (compared to other – namesto xx% je boljših od vas rečemo je nn% projektov imelo podoben rezultat)