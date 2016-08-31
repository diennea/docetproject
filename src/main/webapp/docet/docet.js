var computeAppPath = function() {
    return window.location.pathname.split("main")[0];
};

var language = location.search.split('lang=')[1];
language = language ? language : 'it';

var refpackage = location.pathname.split("/")[3];

var docet = {
		searchUrl: window.location.protocol + "//" + window.location.host + computeAppPath() + "search",
		localization: {
			pageTitle: "Docet",
			mainPageTitle: "Home",
			searchResultTitle: "Search Results",
			searchPackageResultTitle: "Package <strong>${package}</strong>",
			packageResultsFound: "Found ${num} results",
			searchButtonLabel: "Search",
			searchRelevance: "Relevance",
			searchInputPlaceholder: "Enter a search term or sentence...",
			noResultsFound: "Your search <strong>${term}</strong> did not match any documents.",
			someResultsFound: "${num} results for <strong>${term}</strong> in ${numPkg} packages"
		},
		language: language,
		refPackage: refpackage
}

function findPos(obj) {
    var curtop = 0;
    if (obj.offsetParent) {
        do {
            curtop += obj.offsetTop;
        } while (obj = obj.offsetParent);
    return [curtop];
    }
}

function scrollToElement(elementId) {
	$('#' + elementId).attr('tabindex','-1');
	$('#docet-content-anchor').animate({
	    scrollTop: findPos(document.getElementById(elementId))
	  }, 700, function() {
		  $('#' + elementId).focus();
	  });
}

$(document).ready(function() {
	document.title = docet.localization.pageTitle;
	var renderDefaultBreadCrumbs = function() {
		$('#docet-breadcrumbs-anchor').empty();
		$('#docet-breadcrumbs-anchor').append('<a href="'+ document.location.href +'">' + docet.localization.mainPageTitle + '</a>');
	};

	var updateBreadcrumbs = function(menuItem) {
			var crumbs = new Array();
			var parentMenu = menuItem.parent().parent().parent();
			while(parentMenu.length > 0) {
				parentMenu = $(parentMenu.closest("li")).children("div").children('.docet-menu-link');
				var menuTitle = $(parentMenu);
				if (menuTitle.length > 0) {
					crumbs.push(menuTitle);	
				}
				parentMenu = parentMenu.parent().parent().parent();
			}
			renderDefaultBreadCrumbs();
			while(crumbs.length > 0) {
				var currEl = crumbs.pop();
				appendElementToBreadcrumbs(currEl);
			}
			
	};

	var appendElementToBreadcrumbs = function(el) {
			$('#docet-breadcrumbs-anchor').append('<span> / </span>');
			var cssClass;
			var itemId;
			if (el.hasClass('docet-faq-link')) {
				cssClass = 'docet-faq-link docet-menu-link';
				itemId = el.attr('id');
			} else {
				cssClass = 'docet-page-link'
				itemId = getIdForPage(el.attr('href'));
			}
			$('#docet-breadcrumbs-anchor').append('<a id= "' + itemId + '" class="' + cssClass + '" href="' + el.attr('href') + '">' + el.text() +'</a>');
	};

	var closeTocTree = function() {
		$('#docet-menu-anchor ul').removeClass('docet-menu-visible');
		$('#docet-menu-anchor ul').addClass('docet-menu-hidden');
		$('#docet-menu-anchor li>div').removeClass('docet-menu-open');
		$('#docet-menu-anchor li>div').addClass('docet-menu-closed');
		$('#docet-menu-anchor>ul').removeClass('docet-menu-hidden');
		$('#docet-menu-anchor>ul').addClass('docet-menu-visible');
	};
	//FIXME this function is no longer used (keep or start over and use it?)
	/*var closeOpenSubtrees = function() {
		$('#docet-menu-anchor ul:has(.menu-a.selected)').removeClass("docet-menu-visible");
		$('#docet-menu-anchor ul:has(.menu-a.selected)').addClass("docet-menu-hidden");
		$('.menu-a.selected').removeClass("selected");
	}*/
	var expandTreeForPage = function(el) {
	    $('#' + el.attr('id') + ".docet-menu-link").addClass("selected");
	    var $div = $('#' + el.attr('id') + ".docet-menu-link").parent();
	    var liItem = $($div).parent("li");
	    var ulItem = $(liItem).children("ul");
	    $(ulItem).removeClass("docet-menu-hidden");
	    $(ulItem).addClass("docet-menu-visible");
	    $($div).removeClass("docet-menu-closed");
	    $($div).addClass("docet-menu-open");

	    //in case of a 3rd level menu we need to open even the parent ul
	    var parentUl = $(liItem).parent("ul");
	    var parentLi = $(parentUl).parent("li");
	    while (parentUl.length > 0 && parentUl.parent("div").length == 0) {
	    	parentUl.removeClass("docet-menu-hidden");
	    	parentUl.addClass("docet-menu-visible");
	    	parentLi.children("div").removeClass("docet-menu-closed");
	    	parentLi.children("div").addClass("docet-menu-open");

	    	parentUl = parentUl.parent("li").parent("ul");
	    	parentLi =  parentUl.parent("li");
	    }
	};
	var openFaqAnswer = function(e){
		e.preventDefault();
//		var $this = $(e.target);
//		var $answer = $($this).parent().children(".answer");
//		showhideFaqAnswer($answer);
		return false;
	};
	var showhideFaqAnswer = function($answer) {
		$($answer).toggleClass("visible");
	}
	var openPageFromMenu = function(e){
		e.preventDefault();
		var $this = $(e.target);
//		if ($this.attr('id') === 'docet-faq-main-link') {
//			return;
//		} 

		closeTocTree();
		if (!$this.hasClass('docet-menu-link')) {
			return;
		} 

		$('.docet-menu-link.selected').removeClass("selected");
		$.ajax({
			  url: $this.attr('href'),
			})
			  .done(function( data ) {
				expandTreeForPage($this);
			    $("#docet-content-anchor").html(data);
			    if ($($this).parent().attr('id') === 'docet-breadcrumbs-anchor') {
			    	$this = $('#' + $this.attr('id') + ".docet-menu-link");
			    }
			    updateBreadcrumbs($this);
			    $($this).addClass('selected');
			    var $li = $($this).parent().parent();
			    var $div = $($this).parent();
				if (!$this.hasClass('docet-faq-link') || $this.hasClass('docet-faq-mainlink')) {
					$($li).children("ul").removeClass("docet-menu-hidden");
					$($li).children("ul").addClass("docet-menu-visible");
				    $($div).removeClass("docet-menu-closed");
				    $($div).addClass("docet-menu-open");
				} else {
					$($li).children("ul").removeClass("docet-menu-visible");
					$($li).children("ul").addClass("docet-menu-hidden");
				    $($div).removeClass("docet-menu-open");
				    $($div).addClass("docet-menu-closed");
				}
			  });
	};
	var getIdForPage = function(pageLink) {
		var tokens = pageLink.split("/");
		var pageName = tokens.pop();
		return pageName.split(".")[0];
	}

	var getFragmentForPage = function(pageLink) {
		var tokens = pageLink.split("#");
		if (tokens.length == 2) {
			return tokens[1];
		}
		return '';
	}

	var openPageFromPage = function(e){
		e.preventDefault();
		closeTocTree();
		$('.docet-menu-link.selected').removeClass("selected");
		var pageId = $(e.target).attr('id');
		var $this = $('.docet-menu-submenu #' + pageId + ", " + '.docet-menu #' + pageId);
		var tocPresent = true;
		if (!$this.attr('href')) {
			tocPresent = false;
			$this = $(e.target);
		}
		var fragment = getFragmentForPage($(e.target).attr('href'));
		$("#docet-content-anchor").load($this.attr('href'), function() {
			if (tocPresent) { 
		    	expandTreeForPage($this);
		    	var id = $this.attr('id');
		    	
				if (id.startsWith('faq_') && !$this.hasClass('docet-faq-mainlink')) {
					if (!$this.parent().parent().parent().parent().children('div').find('a').hasClass('docet-faq-mainlink')) {
						var anchorInToc = $('#' + id + ".docet-menu-link");
						var ul = $(anchorInToc).parent().parent().parent();
						$(ul).removeClass('docet-menu-visible');
						$(ul).addClass('docet-menu-hidden');
						var li = $(ul).parent().find('div > a').addClass('selected');
					} else {
						var $subUl = $this.parent().parent().children('ul');
						$($subUl).removeClass('docet-menu-visible');
						$($subUl).addClass('docet-menu-hidden');
					}
				}
		    	updateBreadcrumbs($this);
		    }
			if (fragment.length > 0) {
		    	scrollToElement(fragment);
		    }
		});
	};

	var mergeData = function (params) {
		var queryStr = (window.location.search);
    	var additionalParams = new Array();
    	if (queryStr.length > 0) {
    		additionalParams = queryStr.split("?")[1].split("&");
    	}
    	var i;
    	for(i = 0; i < additionalParams.length; i++) {
    		var param = additionalParams[i].split("=");
    		params[param[0]] = param[1];
    	}
    	return params;
    };

    var getSearchTerm = function(params) {
    	var searchTerm;
    	if (params.term) {
    		searchTerm = params.term;
    	} else {
    		searchTerm = params;
    	}
    	return searchTerm;
    };

	var searchPages = function(e) {
		e.preventDefault();
		var queryTerm = $('#docet-search-anchor input').val().trim();
		if (queryTerm.length == 0) {
			//alert ("no search term provided!");
			return;
		}
		closeTocTree();
		renderDefaultBreadCrumbs();
		$.ajax({
			  url: docet.searchUrl,
			  data: mergeData({
				  q: queryTerm,
				  sourcePkg: docet.refPackage,
				  enablePkg: docet.enabledPackages,
				  lang: docet.language
			  })
			})
			  .done(function( data ) {
				  renderSearchResults(data, queryTerm);
			  });
	};

	var renderSearchResults = function(data, term) {
		$('#docet-content-anchor').empty();
		var items = data.results;
		renderSearchPageHeader(data.totalCount, items.length, term);
		var countRes = items.length;
		for(var i=0; i<items.length; i++) {
			var res = items[i];
			renderSearchResultForPackage(res);
		}
	};
	var renderSearchResultForPackage = function (packageRes) {
		var name = packageRes.packagename;
		var items = packageRes.items;
		$('#docet-content-anchor').append('<h2>' + docet.localization.searchPackageResultTitle.replace('${package}', name) + '</h2>');
		$('#docet-content-anchor').append('<span>' + docet.localization.packageResultsFound.replace('${num}', items.length) + '</span>');
		for(var i=0; i<items.length; i++) {
			var res = items[i];
			renderSearchResultItem(res);
		}
	};
	var renderSearchPageHeader = function (numFound, numPkg, term) {
		$('#docet-content-anchor').append('<h1>' + docet.localization.searchResultTitle + '</h1>');
		renderResultsCountMessage(numFound, numPkg, term);
	};
	var renderResultsCountMessage = function (numFound, numPkg, term) {
		var message;
		if (numFound > 0) {
			message = docet.localization.someResultsFound.replace('${num}', numFound).replace('${term}', term).replace('${numPkg}', numPkg);
		} else {
			message = docet.localization.noResultsFound.replace('${term}', term);
		}
		$('#docet-content-anchor').append('<span>' + message + '</span>');
	};
	var renderSearchResultItem = function(res) {
		var div = document.createElement("div");
	    div.className = "docet-search-result";
	    var pageAbstract = document.createElement("p");
	    pageAbstract.className = "docet-abstract";
	    pageAbstract.innerHTML = res.pageAbstract;
	    var pageMatchingExcerpt = document.createElement("p");
	    pageMatchingExcerpt.className = "docet-excerpt";
	    pageMatchingExcerpt.innerHTML = res.matchExplanation;
	    var relevance = document.createElement("p");
	    relevance.className = "docet-relevance";
	    relevance.innerHTML = docet.localization.searchRelevance + ': <b>' + res.relevance + '%</b>';
	    var crumbs = document.createElement("p");
	    crumbs.className = "docet-crumbs";
	    var parseCrumbs = function(crumbArray) {
	    	var res = "";
	    	while (crumbArray.length > 0) {
	    		res += crumbArray.pop() + " > ";
	    	}
	    	var lastIndex = res.lastIndexOf(" > ");
	    	if (lastIndex >= 0) {
	    		res = res.substring(0, lastIndex);
	    	}
	    	return res;
	    };
	    var parsedCrumbs = parseCrumbs(res.breadCrumbs);
	    crumbs.innerHTML = parsedCrumbs;
	    var anchor = document.createElement("a");
	    anchor.href = res.pageLink;
	    anchor.innerHTML = res.title;
	    anchor.id = res.pageId;
	    $(anchor).on("click", function(e){
			e.preventDefault();
			var $this = $(e.target);
			$('.docet-menu-link.selected').removeClass("selected");
			$.ajax({
				  url: $this.attr('href'),
				})
				  .done(function(data) {
					    expandTreeForPage($this);
					    $this = $('.docet-menu-link.selected');
					    updateBreadcrumbs($this);
					    $("#docet-content-anchor").html(data);
					  });
		});
	    div.appendChild(anchor);
	    if (parsedCrumbs.length > 0) {
	    	div.appendChild(crumbs);
	    }
	    div.appendChild(pageAbstract);
	    div.appendChild(relevance);
	    div.appendChild(pageMatchingExcerpt);
	    $('#docet-content-anchor').append(div);
	};
	var expandTocSubMenu = function(e) {
		e.preventDefault();
		var $enclosingDiv = $(e.target);
		var $this = $(e.target).parent('li');
		
		if (!$this.hasClass('docet-menu-submenu') && !$this.hasClass('docet-menu')) {
			return;
		}
		var $subMenu = $($this).children("ul");
		$subMenu.toggleClass("docet-menu-visible");
		$subMenu.toggleClass("docet-menu-hidden");
		$($enclosingDiv).toggleClass('docet-menu-open');
		$($enclosingDiv).toggleClass('docet-menu-closed');
	};
	$(document).on("click", "li.docet-menu-submenu>div", expandTocSubMenu);
	$(document).on("click", "li.docet-menu>div", expandTocSubMenu);

	$(document).on("click", "#docet-faq-main-link", function(e){
		e.preventDefault();
		var $this = $('li.docet-menu > div > a#docet-faq-main-link');
		var $subMenu = $($this).parent('div').siblings('ul');
		$subMenu.toggleClass("docet-menu-hidden");
		$subMenu.toggleClass("docet-menu-visible");
		$($this).parent().toggleClass('docet-menu-closed');
		$($this).parent().toggleClass('docet-menu-open');
		if ($($this).parent().hasClass('docet-menu-open') && !$($this).parent('div').siblings('ul').children('li').children('div').hasClass('docet-menu-open')) {
			$($this).addClass('selected');
		} else {
			$($this).removeClass('selected');
		}
	});
	$(document).on("click", ".docet-menu-link", openPageFromMenu);
	$(document).on("click", ".docet-page-link", openPageFromPage);
	$('#docet-menu-anchor').on("click", ".docet-page-link", openPageFromPage);
	$("#docet-content-anchor").on("click", "a.question", openFaqAnswer);
	
	$('#docet-search-anchor').html('<div class="docet-search"><input id="docet-search-field" class="docet-search-field" type="text" placeholder="' + docet.localization.searchInputPlaceholder + '" /><span class="docet-search-controls" id="docet-search-controls" title="' + docet.localization.searchButtonLabel + '"></span></div>');
        $(document).on("click", "#docet-search-anchor #docet-search-controls", searchPages);
	$('#docet-search-anchor #docet-search-field').bind("enterPressed",function(e){
		searchPages(e);
	});
	$('#docet-search-anchor #docet-search-field').keyup(function(e){
		if(e.keyCode == 13)
		{
			$(this).trigger("enterPressed");
		}
	});
	renderDefaultBreadCrumbs();
});


