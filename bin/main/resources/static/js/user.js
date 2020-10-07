var token = null;

$(document).ready(function(){
	
	token = localStorage.getItem("token");
	
	$('#logout').on('click',function(e){
		localStorage.removeItem("token");
		window.location.href = "https://localhost:8443/";
	});

});