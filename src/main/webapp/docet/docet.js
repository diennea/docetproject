var Docet = (function ($, document) {
	'use strict';
	var res = {};

	var language = function() {
		var res = location.search.split('lang=')[1];
		res = res ? res : "it";
		return res;
	}();

	var computeAppPath = function() {
	    return window.location.pathname;
	};

	var getBaseURL = function() {
		return window.location.protocol + "//" + window.location.host + computeAppPath();
	}

	var docet = {
			urls: {
				search: getBaseURL() + "search",
				toc: getBaseURL() + "toc",
				packagelist: getBaseURL() + "package"
			},
			search: {
				pagination: 5
			},
			localization: {
				language: language,
				pageTitle: "Docet",
				mainPageTitle: "Package List",
				mainPageDescription: "Here is a list of available packages",
				searchResultTitle: "Search Results",
				searchPackageResultTitle: "Package <strong>${package}</strong>",
				showMoreResults: "Show more...",
				showLessResults: "Show less...",
				packageResultsFound: "Found ${num} results",
				searchButtonLabel: "Search",
				searchRelevance: "Relevance",
				searchInputPlaceholder: "Enter a search term or sentence...",
				noResultsFound: "Your search <strong>${term}</strong> did not match any documents.",
				someResultsFound: "${num} results for <strong>${term}</strong> in ${numPkg} packages"
			},
			profile: {
				showPageId: true
			},
			packages: {
				
			},
			elements: {
				content: '#docet-content-anchor',
				menu: '#docet-menu-anchor',
				search: '#docet-search-anchor',
				breadcrumbs: 'docet-breadcrumbs-anchor'
			}
	};

	var getCurrentPackage = function() {
		return $(docet.elements.menu + ' > ul.docet-menu').attr('package');
	};

	var updatePackageDescription = function(id, values) {
		docet.packages[id] = values;
	}

	var mergeData = function(params) {
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

	var buildQueryString = function(params) {
		var res = '?';
		var mergedParams = mergeData(params);
		for (var k in mergedParams) {
			res += k + '=' + mergedParams[k] + "&";
		}
		if (res.length == 1) {
			return '';
		}
		return res.substring(0, res.length -1);
	}

	var loadPackageList = function() {
		var renderPackageList = function (data) {
			var packages = data.items;
			var divMessage = document.createElement("div");
			var pMessage = document.createElement("p");
			pMessage.innerHTML = docet.localization.mainPageDescription;
			divMessage.appendChild(pMessage);
			$(docet.elements.content).append(divMessage);

			var divList = document.createElement("div");
			divList.className = "docet-package-list";
			$(docet.elements.content).append(divList);
			for(var i=0; i<packages.length; i++) {
				var res = packages[i];
				renderPackageItem(res);
			}
		};
		var renderPackageItem = function (res) {
			var div = document.createElement("div");
		    div.className = "docet-package-item";

			var divContainer = document.createElement("div");
			divContainer.className = "docet-package-item-container";
			div.appendChild(divContainer);

		    var pkgAbstract = document.createElement("p");
		    pkgAbstract.className = "docet-package-abstract";
		    pkgAbstract.innerHTML = res.description;

		    var anchor = document.createElement("a");
		    anchor.className = 'docet-package-item-title docet-menu-link';
		    anchor.innerHTML = res.title;
		    anchor.setAttribute('href',res.packageLink);
		    anchor.setAttribute('package', res.packageId);
		    updatePackageDescription(res.packageId, {link: res.packageLink, label: res.title});
		    anchor.id = "package-" + res.packageId;

		    var iconAnchor = document.createElement("a");
		    iconAnchor.className = 'docet-menu-link';
		    iconAnchor.setAttribute('href',res.packageLink);
		    iconAnchor.setAttribute('package', res.packageId);
		    var pkgIcon = document.createElement('img');
		    pkgIcon.className = 'docet-package-item-icon';
		    pkgIcon.setAttribute("src", res.imageLink);
		    iconAnchor.appendChild(pkgIcon);

		    var header = document.createElement("div");
		    header.className = "docet-package-item-header";
		    header.appendChild(iconAnchor);
		    header.appendChild(anchor);

		    var body = document.createElement("div");
		    body.className = "docet-package-item-body";
		    body.appendChild(pkgAbstract);

		    divContainer.appendChild(header);
		    divContainer.appendChild(body);
		    $('div.docet-package-list').append(div);
		}

		var servleturl = docet.urls.packagelist;

	    $.ajax({
	        url: servleturl,
	        data : mergeData({
	        	lang: docet.localization.language,
	        	id: docet.packages.list
	        }),
	        async: true,
	        method: 'GET',
	        dataType: 'json',
	        traditional: true,
	        success: function (response) {
	        	renderPackageList(response);
	        },
	        error: function (response) {
	            noop();
	        }
	    });
	}
	
	var hideToc = function() {
		$(docet.elements.menu).removeClass('docet-menu-container-visible');
		$(docet.elements.menu).addClass('docet-menu-container-hidden');
	}

	var showToc = function() {
		$(docet.elements.menu).removeClass('docet-menu-container-hidden');
		$(docet.elements.menu).addClass('docet-menu-container-visible');
	}

	var renderPageId = function() {
		if (docet.profile.showPageId) {
			$('#docet-page-id').css('visibility', 'visible');
			$('#docet-page-id').css('display', 'block');
		}
	}

	var findPos = function(obj) {
	    var curtop = 0;
	    if (obj.offsetParent) {
	        do {
	            curtop += obj.offsetTop;
	        } while (obj = obj.offsetParent);
	    return [curtop];
	    }
	}

	var scrollToElement = function(elementId) {
		$('#' + elementId).attr('tabindex','-1');
		$(docet.elements.content).animate({
		    scrollTop: findPos(document.getElementById(elementId))
		  }, 700, function() {
			  $('#' + elementId).focus();
		  });
	}

	var renderDefaultBreadCrumbs = function() {
		$(docet.elements.breadcrumbs).empty();
		$(docet.elements.breadcrumbs).append('<a href="'+ getBaseURL() + buildQueryString({})  +'">' + docet.localization.mainPageTitle + '</a>');
		if (docet.packages.current) {
			var packageDesc = docet.packages[docet.packages.current];
			$(docet.elements.breadcrumbs).append('<span> / </span>');
			$(docet.elements.breadcrumbs).append('<a class="docet-page-link" package="' + docet.packages.current + '" href="'+ packageDesc.link +'">' + packageDesc.label + '</a>');
		}
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
			$(docet.elements.breadcrumbs).append('<span> / </span>');
			var cssClass;
			var itemId;
			if (el.hasClass('docet-faq-link')) {
				cssClass = 'docet-faq-link docet-menu-link';
				itemId = el.attr('id');
			} else {
				cssClass = 'docet-page-link'
				itemId = getIdForPage(el.attr('href'));
			}
			$(docet.elements.breadcrumbs).append('<a id= "' + itemId + '" class="' + cssClass + '" href="' + el.attr('href') + '">' + el.text() +'</a>');
	};

	var closeTocTree = function() {
		$(docet.elements.menu + ' ul').removeClass('docet-menu-visible');
		$(docet.elements.menu + ' ul').addClass('docet-menu-hidden');
		$(docet.elements.menu + ' li > div').removeClass('docet-menu-open');
		$(docet.elements.menu + ' li > div').addClass('docet-menu-closed');
		$(docet.elements.menu + ' > ul').removeClass('docet-menu-hidden');
		$(docet.elements.menu + ' > ul').addClass('docet-menu-visible');
	};

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

	var loadTocTreeForPackage = function(link, packageId) {
		if (!packageId) {
			return;
		}
		var currentTocPackage = $(docet.elements.menu + ' > ul.docet-menu').attr('package');
		if ( currentTocPackage !== undefined && currentTocPackage === packageId) {
			showToc();
			return;
		}
		$.ajax({
			  url: docet.urls.toc,
			  data: mergeData({
				  packageId: packageId,
				  lang: docet.localization.language
			  })
			})
			  .done(function( data) {
				  $(docet.elements.menu).empty();
				  $(docet.elements.menu).html(data);
				  expandTreeForPage(link);
				  showToc();
			  });
	};

	var setCurrentPackage = function(target) {
		docet.packages.current = target.attr('package');

	};

	var openPageFromImage = function(e) {
		e.target = $(e.target).parent();
		openPageFromMenu(e);
	}

	var openPageFromMenu = function(e) {
		e.preventDefault();
		var $this = $(e.target);
		loadTocTreeForPackage($this, $this.attr('package'));
		closeTocTree();
		if (!$this.hasClass('docet-menu-link')) {
			return;
		} 
	    setCurrentPackage($this);
		$('.docet-menu-link.selected').removeClass("selected");
		$.ajax({
			  url: $this.attr('href'),
			})
			  .done(function( data ) {
				expandTreeForPage($this);
			    $(docet.elements.content).html(data);
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
				renderPageId();
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
		loadTocTreeForPackage($(e.target), $(e.target).attr('package'));
		var $this = $('.docet-menu-submenu #' + pageId + ", " + '.docet-menu #' + pageId);
		var tocPresent = true;
		if (!$this.attr('href')) {
			tocPresent = false;
			$this = $(e.target);
		}
		var fragment = getFragmentForPage($(e.target).attr('href'));
		$(docet.elements.content).load($this.attr('href'), function() {
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
			renderPageId();
		});
	};

	var searchPages = function(e) {
		e.preventDefault();
		var queryTerm = $(docet.elements.search + ' input').val().trim();
		if (queryTerm.length == 0) {
			//alert ("no search term provided!");
			return;
		}
		closeTocTree();
		renderDefaultBreadCrumbs();
		$.ajax({
			  url: docet.urls.search,
			  data: mergeData({
				  q: queryTerm,
				  sourcePkg: getCurrentPackage(),
				  enablePkg: docet.packages.list,
				  lang: docet.localization.language
			  })
			})
			  .done(function( data ) {
				  renderSearchResults(data, queryTerm);
			  });
	};

	var renderSearchResults = function(data, term) {
		hideToc();
		$(docet.elements.content).empty();
		var items = data.results;
		var numFoundPkgs = items.length;
		if (data.currentPackageResults) {
			numFoundPkgs++;
		}
		renderSearchPageHeader(data.totalCount, numFoundPkgs, term);
		if (data.currentPackageResults) {
			renderSearchResultForPackage(data.currentPackageResults);
		}
		var countRes = items.length;
		for(var i=0; i<items.length; i++) {
			var res = items[i];
			renderSearchResultForPackage(res);
		}
	};

	var renderSearchResultForPackage = function (packageRes) {
		var name = packageRes.packagename;
		var pkgId = packageRes.packageid;
		var pkgLink = packageRes.packagelink;
		var items = packageRes.items;
		updatePackageDescription(pkgId, {link: pkgLink, label: name});
		$(docet.elements.content).append('<h2>' + docet.localization.searchPackageResultTitle.replace('${package}', name) + '</h2>');
		$(docet.elements.content).append('<div id="page-res-' + pkgId + '"></div>');
		$(docet.elements.content).append('<div id="data-res-' + pkgId + '"></div>');
		$('#page-res-' + pkgId).pagination({
		    dataSource: items,
		    pageSize: 5,
		    className: 'paginationjs paginationjs',
		    showPrevious: true,
		    showNext: true,
		    showNavigator: true,
		    formatNavigator: '<span>' + docet.localization.packageResultsFound.replace('${num}', items.length) + '</span>',
		    position: 'top',
		    callback: function(data, pagination) {
		        var html = templateResultItemsForPackage(data);
		        $('#data-res-' + pkgId).html(html);
		    }
		})
	};

	var renderSearchPageHeader = function (numFound, numPkg, term) {
		$(docet.elements.content).append('<h1>' + docet.localization.searchResultTitle + '</h1>');
		renderResultsCountMessage(numFound, numPkg, term);
	};

	var renderResultsCountMessage = function (numFound, numPkg, term) {
		var message;
		if (numFound > 0) {
			message = docet.localization.someResultsFound.replace('${num}', numFound).replace('${term}', term).replace('${numPkg}', numPkg);
		} else {
			message = docet.localization.noResultsFound.replace('${term}', term);
		}
		$(docet.elements.content).append('<span>' + message + '</span>');
	};

	var templateResultItemsForPackage = function (data) {
		var html = '';
		$.each(data, function(index, item) {
			html += templateSearchResultItem(item);
			});
		return html;
	};

	var templateSearchResultItem = function(res) {
		var div = document.createElement("div");
	    div.className = "docet-search-result ";
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
	    	for (var i = 0; i < crumbArray.length; i++) {
	    		res += crumbArray[i] + " > ";
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
	    anchor.className = 'docet-menu-link';
	    anchor.setAttribute("package", res.packageId);
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
					    $("docet.elements.content").html(data);
					  });
		});
	    div.appendChild(anchor);
	    if (parsedCrumbs.length > 0) {
	    	div.appendChild(crumbs);
	    }
	    div.appendChild(pageAbstract);
	    div.appendChild(relevance);
	    div.appendChild(pageMatchingExcerpt);
	    return div.outerHTML;
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

	var isEmpty = function (str) {
	    return (!str || 0 === str.length);
	};

	var initConfiguration = function (conf) {
		var urls = conf.urls;
		if (urls) {
			for (var key in urls) {
				var value = urls[key];
				if (!isEmpty(value)) {
					docet.urls[key] = urls[key];
				}
			}
		}
		var search = conf.search;
		if (search) {
			for (var key in search) {
				var value = search[key];
				if (!isEmpty(value)) {
					docet.search[key] = search[key];
				}
			}
		}
		var localization = conf.localization;
		if (localization) {
			for (var key in localization) {
				var value = localization[key];
				if (!isEmpty(value)) {
					docet.localization[key] = localization[key];
				}
			}
		}
		var profile = conf.profile;
		if (profile) {
			for (var key in profile) {
				var value = profile[key];
				if (!isEmpty(value)) {
					docet.profile[key] = profile[key];
				}
			}
		}
		var packages = conf.packages;
		if (packages) {
			for (var key in packages) {
				var value = packages[key];
				if (!isEmpty(value)) {
					docet.packages[key] = packages[key];
				}
			}
		}
		var elements = conf.elements;
		if (elements) {
			for (var key in elements) {
				var value = elements[key];
				if (!isEmpty(value)) {
					docet.elements[key] = elements[key];
				}
			}
		}
	};

	var hookHandlers = function () {
		$(document).on("click", "li.docet-menu-submenu>div", expandTocSubMenu);
		$(document).on("click", "li.docet-menu>div", expandTocSubMenu);
		$(document).on("click", "#docet-faq-main-link", function(e) {
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
		$(document).on("click", ".docet-menu-link img", openPageFromImage);
		$(document).on("click", ".docet-page-link", openPageFromPage);
		$(docet.elements.menu).on("click", ".docet-page-link", openPageFromPage);

		$(docet.elements.search).html('<div class="docet-search"><input id="docet-search-field" class="docet-search-field" type="text" placeholder="' + docet.localization.searchInputPlaceholder + '" /><span class="docet-search-controls" id="docet-search-controls" title="' + docet.localization.searchButtonLabel + '"></span></div>');
	        $(document).on("click", docet.elements.search + " #docet-search-controls", searchPages);
		$(docet.elements.search + ' #docet-search-field').bind("enterPressed",function(e){
			searchPages(e);
		});
		$(docet.elements.search + ' #docet-search-field').keyup(function(e){
			if(e.keyCode == 13)
			{
				$(this).trigger("enterPressed");
			}
		});
		$(docet.elements.menu).toggleClass('docet-menu-container-visible');
	};

	var initPage = function() {
		document.title = docet.localization.pageTitle;
		renderDefaultBreadCrumbs();
	    hideToc();
	    loadPackageList();
	};

	res.init = function(config) {
		initConfiguration(config);
		hookHandlers();
		initPage();
	};

	return res;
})(jQuery, document);
