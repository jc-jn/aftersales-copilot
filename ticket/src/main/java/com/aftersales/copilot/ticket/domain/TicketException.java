package com.aftersales.copilot.ticket.domain;
import org.springframework.http.HttpStatus;
public class TicketException extends RuntimeException {
 private final String code; private final HttpStatus status; private final Object details;
 public TicketException(String code,String message,HttpStatus status){this(code,message,status,null);}
 public TicketException(String code,String message,HttpStatus status,Object details){super(message);this.code=code;this.status=status;this.details=details;}
 public String code(){return code;} public HttpStatus status(){return status;} public Object details(){return details;}
 public static TicketException notFound(){return new TicketException("TICKET_NOT_FOUND","工单不存在",HttpStatus.NOT_FOUND);}
 public static TicketException notOwned(){return new TicketException("TICKET_NOT_OWNED","无权访问该工单",HttpStatus.FORBIDDEN);}
 public static TicketException activeExists(){return new TicketException("ACTIVE_TICKET_EXISTS","该订单项已有活动工单",HttpStatus.CONFLICT);}
 public static TicketException invalid(String from,String action){return new TicketException("TICKET_INVALID_TRANSITION","当前状态不允许执行该操作",HttpStatus.CONFLICT,java.util.Map.of("currentStatus",from,"action",action));}
 public static TicketException version(){return new TicketException("TICKET_VERSION_CONFLICT","工单已被其他请求修改，请刷新后重试",HttpStatus.CONFLICT);}
}
