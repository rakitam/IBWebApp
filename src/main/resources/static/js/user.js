var token = null;

$(document).ready(function(){
	
	token = localStorage.getItem("token");
	

	$('#logout').on('click',function(e){
		localStorage.removeItem("token");
		window.location.href = "https://localhost:8443/";
	});
	$('#download').on('click',function(e){
		var token = localStorage.getItem("token");
		$.ajax({
			headers:{"Authorization" :"Bearer " + token},
			contentType: 'application/json',
			type: 'GET',
			dataType:'json',
			crossDomain: true,
			url:'https://localhost:8443/api/users/whoami/download',
			success:function(response){
				
				console.log(response)
		
			},
			error: function (jqXHR, textStatus, errorThrown) { 
				console.log(jqXHR);
				alert(textStatus);
			}
		});
	});
});