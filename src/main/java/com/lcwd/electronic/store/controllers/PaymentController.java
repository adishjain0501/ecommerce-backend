package com.lcwd.electronic.store.controllers;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lcwd.electronic.store.dtos.OrderDto;
import com.lcwd.electronic.store.dtos.UserDto;
import com.lcwd.electronic.store.services.OrderService;
import com.lcwd.electronic.store.services.UserService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;

@RequestMapping("/payments")
@RestController
public class PaymentController {
	
	@Autowired
	private UserService userService;
	@Autowired
	private OrderService orderService;
	@Value("${razorpayKey}")
	private String razorpayKey;
	@Value("${razorpaySecret}")
	private String razorpaySecret;
	
	@PostMapping("initiate-payment/{orderId}")
	public ResponseEntity<?> initiatePayment(@PathVariable String orderId,Principal principal) {
		UserDto userDto = this.userService.getUserByEmail(principal.getName());
		OrderDto orderDto = this.orderService.getOrder(orderId);
		try {
			// razor pay api to create order
			RazorpayClient razorpay = new RazorpayClient(razorpayKey, razorpaySecret);
			JSONObject orderRequest = new JSONObject();
			orderRequest.put("amount",orderDto.getOrderAmount()*100); // Amount is in currency subunits. Default currency is INR. Hence, 50000 refers to 50000 paise
			orderRequest.put("currency","INR");
			orderRequest.put("receipt", "receipt#1");
//			JSONObject notes = new JSONObject();
//			notes.put("notes_key_1","Tea, Earl Grey, Hot");
//			orderRequest.put("notes",notes);
			// create order
			Order order = razorpay.orders.create(orderRequest);
			//save the order id to backend
			System.out.println(order);
			
			orderDto.setRazorPayOrderId(order.get("id"));
			this.orderService.updateOrder(orderId, orderDto);
			
			// Convert Razorpay Order to Map for serialization
			Map<String, Object> response = new HashMap<>();
			response.put("orderId", orderDto.getOrderId());
	        response.put("razorpayOrderId", order.get("id"));
	        response.put("paymentStatus", order.get("status"));
	        response.put("amount", orderDto.getOrderAmount());
	        response.put("currency", order.get("currency"));
			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		}
		catch(RazorpayException ex) {
			ex.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message","Error in creating order !!"));
		}
	}
	
	@PostMapping("/verify-and-save-payment/{orderId}")
	public ResponseEntity<?> verifyAndSavePayment(@RequestBody Map<String,Object>data,@PathVariable String orderId){
			String razorpayOrderId = (String) data.get("razorpayOrderId");
			String razorpayPaymentId = (String) data.get("razorpayPaymentId");
			String razorpaySignature = (String) data.get("razorpayPaymentSignature");
			
			OrderDto orderDto = this.orderService.getOrder(orderId);
			orderDto.setPaymentStatus("PAID");
			orderDto.setPaymentId(razorpayPaymentId);
			// we can also store razorpay signatures
			this.orderService.updateOrder(orderId, orderDto);
			try {
				RazorpayClient razorpay = new RazorpayClient(razorpayKey, razorpaySecret);

//				String secret = "EnLs21M47BllR3X8PSFtjtbd";

				JSONObject options = new JSONObject();
				options.put("razorpay_order_id", razorpayOrderId);
				options.put("razorpay_payment_id", razorpayPaymentId);
				options.put("razorpay_signature", razorpaySignature);

				boolean status =  Utils.verifyPaymentSignature(options, razorpaySecret);
				if(status) {
					System.out.println(">>>>>>>>>>>>> PAYMENT SIGNATURE VERIFIED !! >>>>>>>>>>>>>>");
					return new ResponseEntity<>(Map.of("message","Payment done","success",true,"signatureVerified",true),HttpStatus.OK);
				}
				else {
					System.out.println(">>>>>>>>>>>>> PAYMENT SIGNATURE VERIFICATION FAILED !! >>>>>>>>>>>>>>");
					return new ResponseEntity<>(Map.of("message","Payment done","success",true,"signatureVerified",false),HttpStatus.OK);
				}
			}
			catch(RazorpayException re) {
				re.printStackTrace();
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
	}

}
