/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ascensor;
import java.util.Random;
import ascensor.GNA;
import ascensor.Ascensor;
import java.util.Collections;
import java.util.ArrayList;
import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.distribution.WeibullDistribution;

/**
 * Javadoc de las distribuciones de la libreria Apache Math
 * https://commons.apache.org/proper/commons-math/apidocs/org/apache/commons/math3/distribution/package-summary.html
 * 
*/

/**
 *
 * @author eduardogasser
 */
public class Main {
    /* distribuciones */ 
    private WeibullDistribution weibull;
    private ExponentialDistribution exponential;
    
    /* variables constantes del programa */
    private static final int alturaPiso = 21; // altura de un piso metros
    private static final int velAscensor = 3; // velocidad del ascensor metros/segundos
    
    private static final int Tviaje = alturaPiso/velAscensor; // tempo de viaje
    private static final int Tabrir = 1;  // tiempo abre puertas ascensor
    private static final int Tcerrar =  3; // tiempo cerrar puertas ascensor
    private static final int Tin_out =  2; // tiempo de espera en cada planta para que salga y entre gente
    
    private static final int MAX_PASAJEROS = 10; // ocupación total del ascensor
    private static final int MAX_PISOS = 5; // número de pisos edificio
    
    
    /* cola de pasajeros que quieren subir*/
    private ArrayList<Cola> cola_subida;
    /* cola de pasajeros que quieren subir */
    private ArrayList<Cola> cola_bajada;
    
    /* lista de personas en reflexion */
    private ArrayList<Pasajero> pasajeros_reflexion;
    
    private int piso_actual  = 0; // piso donde se encuentra el ascensor
    private int piso_destino = 0; // piso destino del ascensor

    private int internas[] = new int[MAX_PISOS]; // llamadas desde la botonera interna
    private int subidas[] = new int[MAX_PISOS]; // llamadas subiendo 
    private int bajadas[] = new int[MAX_PISOS]; // llamadas bajando
    
    private static final int SEMILLA = 14;
    private static final int INFINITO = 10000000;
    
    private Ascensor ascensor;    
    private Event_list event_list = new Event_list();
      
    private int clock;   
    private int number_delayed;
    private int time_of_last_arrival; // instante de la última llegada pasajero
    private float area_under_q; // área del número de pasajeros en espera acumulada (area under Q(t))
    
    /* variables mias para probar, se tienen que eliminar */
    private float total_delayed=0;
    private ArrayList orden = new ArrayList<>();
    private ArrayList ordenclock = new ArrayList<>();
    private ArrayList tiempo_espera = new ArrayList<>();
    /* rutina de inicializacion */
    public void inicializar()
    {
        clock = 0;
        number_delayed = 0;
        ascensor = new Ascensor();
        event_list.setL(100000);
        event_list.setS(10000000);
        event_list.setR(100000000);
        cola_subida = new ArrayList<Cola>();
        cola_bajada = new ArrayList<Cola>();
        pasajeros_reflexion = new ArrayList<Pasajero>();
        for (int i = 0; i<MAX_PISOS; i++){
            cola_subida.add(new Cola("ASC"));
            cola_bajada.add(new Cola("DESC"));
        }
    }
     /* generar piso diferente al actual al que irá el pasajero */
     public int determinar_piso(int p)
     {
        
        int r = randomInterval(1,MAX_PISOS);
        while (p == r){
         r = randomInterval(1,MAX_PISOS);  
        }
        return r;
     }
    /* distribucion Poisson con lambda = 1/lambda para la llegada de pasajeros al edificio */
    private int GA()
    {
       /* exponential = new ExponentialDistribution(0.016);
        //System.out.println(Math.round(exponential.sample() * 1000));
        return (float) (exponential.sample()*1000);*/
        
        return  (int) (-Math.log(1- randomIntervalDouble(0,1)) / 0.016);
    }
    /* distribucion Weibull para el tiempo de reflexión de los pasajeros */
    private int GR()
    {
       weibull = new WeibullDistribution(2, 22);
       return (int) weibull.sample();
    }
    private int randomInterval(int a, int b){
        GNA g = new GNA();
        // por transformada inversa
        return (int)((b-a)*g.rand2(SEMILLA-4)+a);
    }
    private double randomIntervalDouble(int a, int b){
        GNA g = new GNA();
        // por transformada inversa
        return ((b-a)*g.rand2(SEMILLA-4)+a);
    }
    private int determinarPisoProbabilidad(int p){
        int resultado = 0;

        GNA g = new GNA();
        
        if ( g.rand2(SEMILLA) < 0.6 ){
            resultado = randomInterval(1,MAX_PISOS);
            while (resultado == p){
                resultado = randomInterval(1,MAX_PISOS);
            }
        }
       return resultado;
  }
    
  public void mostrar_botonera(int b[], String s)
  {
      System.out.println("  ----BOTONERA " + s + "--");
      for (int i = 0; i<MAX_PISOS; i++)
      {
          System.out.print("    " + b[i] +"|");
      }
      System.out.println("");
  }
 
   /* nos devuelve el pasajero que acabó su tiempo de reflexión y ademas le
    * asignamos el piso al que debe ir
    */
  public Pasajero getPasajeroFinReflexion()
  {
      int i = 0;
      boolean salir = false;
      
      while (i < pasajeros_reflexion.size() && !salir)
      {
          if (pasajeros_reflexion.get(i).getTiempoReflexion() == event_list.getR())
          {
              salir = true;
          }
          i++;
      }
      return pasajeros_reflexion.get(i-1);
  }
  
  public void removePasajeroFinReflexion(){
      int i = 0;
      boolean salir = false;
      
      while (i < pasajeros_reflexion.size() && !salir)
      {
          if (pasajeros_reflexion.get(i).getTiempoReflexion() == event_list.getR())
          {
              pasajeros_reflexion.remove(i);
              salir = true;
          }
          i++;
      }
 
  }
      /* rutina de fin tiempo reflexión pasajero en la planta*/
  public void fin_reflexion()
     {    
          /* CASOS:
          * Que llegue el pasajero y encuentre el ascensor en la misma planta con la misma direccion
          *     entra al ascensor
          * Que llegue el pasajero y no encuentre el ascensor
          *     hace una llamada con su direcccion
          */
         
         System.out.println("     FIN REFLEXIÓN PASAJERO clock: " + clock);
         /* obtengo piso actual del pasajero */
         int piso_actual_pasajero = getPasajeroFinReflexion().getPisoActual();
         /* determino piso al que irá el pasajero */
         int piso_destino_pasajero = determinarPisoProbabilidad(piso_actual_pasajero);
         /* actualizamos piso destino en pasajero */
         getPasajeroFinReflexion().setPisoDestinoPasajero(piso_destino_pasajero);
         
         
         /* Direccion del pasajero */
         String direc;
         if (piso_actual_pasajero < piso_destino_pasajero){ direc = "ASC"; }else{ direc = "DESC";}
       
         /* si ascensor está en el piso actual, está en reposo y tiene misma direccion o no hay nadie dentro del ascensor */
         if (piso_actual == piso_actual_pasajero && !ascensor.getViajando() &&  (ascensor.getDireccion().equals(direc))){
           
                
                /* por si el pasajero entró al ascensor aunque la dirección del ascensor fuese distinta */
                actualizarDireccion(piso_actual_pasajero, piso_destino_pasajero);
                
                /* pasajero sube al ascensor */
                ascensor.getPasajeros().add(getPasajeroFinReflexion());
                /* sacamos al pasajero de la cola de reflexion */
                removePasajeroFinReflexion();
                /* se genera tiempo salida_ascensor */
                event_list.setS(clock + Tin_out);  
                internas[piso_destino_pasajero] = 1; // actualizamos botonera interna
                
                
                mostrar_botonera(internas, "INTERNA");
                mostrar_botonera(subidas,"SUBIDAS");
                mostrar_botonera(bajadas,"BAJADAS");
                
                
                
                System.out.println(" sube al ascensor - Piso actual:" + piso_actual_pasajero +" piso destino: " + piso_destino_pasajero + " genera salida_ascensor t: " + event_list.getS() + " clock:"  + clock);
                System.out.println(" DIRECCION ASCENSOR: " + ascensor.getDireccion() + " DIRECCION PASAJERO: " + direc);
          }else{
             
               System.out.println(" El ascensor esta en el mismo piso pero tiene diferente direccion o ya ha salido");
               System.out.println(" DIRECCION ASCENSOR: " + ascensor.getDireccion() + " DIRECCION PASAJERO: " + direc);
               System.out.println(" Piso actual:" + piso_actual_pasajero +" Piso destino: " + piso_destino_pasajero + " | salida_ascensor t: " + event_list.getS());               
                
                if (direc.equals("ASC")){
                    cola_subida.get(piso_actual_pasajero).add(getPasajeroFinReflexion());
                    removePasajeroFinReflexion();
                    subidas[piso_actual_pasajero] = 1; // actualizamos botonera subida en planta baja
  
                }else{
                    cola_bajada.get(piso_actual_pasajero).add(getPasajeroFinReflexion());
                    removePasajeroFinReflexion();
                    bajadas[piso_actual_pasajero] = 1; // actualizamos botonera subida en planta baja
                 }
                mostrar_botonera(internas, "INTERNAS");
                mostrar_botonera(subidas, "SUBIDAS");
                mostrar_botonera(bajadas, "BAJADAS");
               
            
         }
         mostrar_pasajeros_actuales(); 
         
         event_list.setR(INFINITO);
         System.out.println(" proxima salida_ascensor t: " + event_list.getS());
         System.out.println(" ///////////////////////////////////////");
     }
     /* rutina de llegada pasajero al edificio */
     public void llegada_pasajero()
     {    
         System.out.println("     LLEGADA PASAJERO clock: " + clock);
         /* determino piso al que irá el pasajero */
         int piso_destino_pasajero = determinar_piso(0);

         /* si ascensor está en el piso actual y está en reposo */
         if (piso_actual == 0 && !ascensor.getViajando()){
             
            /* pasajero sube al ascensor */
            ascensor.setPasajeroSube(new Pasajero("ASC", piso_destino_pasajero, clock));
            number_delayed++;

            /* se genera tiempo salida_ascensor */
            event_list.setS(clock + GA());  
            
            /* actualizamos botonera interna */
            internas[piso_destino_pasajero] = 1; 
            
            //mostrar_botonera(internas, "INTERNA");
           
           
            System.out.println(" PASAJERO SUBE - genera salida_ascensor t: " + event_list.getS() + " PISO DESTINO: " + piso_destino_pasajero+" clock:"  + clock);

         }else{ // el ascensor está en otro piso o se acaba de ir
             
            /* calculamos el area de número pasajeros en cola de espera */
            area_under_q += cola_subida.get(0).size() * (clock - time_of_last_arrival);
            time_of_last_arrival = clock; 
            
            /* ponemos al pasajero en la cola : number_in_queue++ */
            cola_subida.get(0).add( new Pasajero("ASC", piso_destino_pasajero,clock) );
            subidas[0] = 1; // actualizamos botonera subida en planta baja
            
            //System.out.println("llegada_pasajeros - el ascensor no está - clock: " +  clock);
            //mostrar_botonera(subidas, "SUBIDAS");
           
         }
         /* se genera el tiempo de llegada del pasajero al edificio */
         event_list.setA(clock + GA());
         
         System.out.println(" proxima llegada_pasajero t: " + event_list.getA()  + " number_delayed: "  + number_delayed);
         System.out.println(" ///////////////////////////////////////");
     }
    
     /* rutina de llegada ascensor a la planta */
     public void llegada_ascensor()
     {
         System.out.println("     ASCENSOR LLEGADA clock: " + clock);
         /* actualizamos el piso y el estado del ascensor */
         actualizarPisoAscensor();
        
         System.out.println(" piso actual: "+ piso_actual+" direccion: "+ ascensor.getDireccion() + " num pasajeros: " + ascensor.getNumPasajeros());
         
         ascensor.setViajando(false);
       
         /* actualizamos botonera interna */
         internas[piso_actual] = 0;
        
         mostrar_botonera(internas, "INTERNA");
         mostrar_botonera(subidas, "SUBIDA");
         mostrar_botonera(bajadas, "BAJADAS");
         
         /* bajamos a las personas del ascensor */
         if (existe_pasajero_irse())
         {
             System.out.println("----------------- SE BAJAN PASAJEROS --------");
             bajar_pasajeros_ascensor();  
         }else{
             System.out.println(" NADIE BAJA - direccion: " + ascensor.getDireccion());
         }
        /* se genera el tiempo de salida del ascensor */
        event_list.setS(clock + Tin_out);  
      
        mostrar_pasajeros_actuales();
        
        actualizarDireccion();
        
        event_list.setL(INFINITO);
        System.out.println(" SALIDA_ASCENSOR t: " + event_list.getS()  + " number_delayed: "  + number_delayed);
        System.out.println(" ///////////////////////////////////////");    
     }
     
     public void bajar_pasajeros_ascensor()
     {
         int i = 0;
         System.out.println(".................PASAJEROS HAY: " + ascensor.getPasajeros().size());

         while (i < ascensor.getPasajeros().size())
         {
             
            if (ascensor.getPasajeros().get(i).getPisoDestino() == piso_actual)
             {
   
                 if (piso_actual == 0){
                     
                    System.out.println(".................Pasajero deja el edificio: " + piso_actual);                
                    ascensor.getPasajeros().remove(i);
                    
                 }else{
                     
                   System.out.println(".................pasajero baja "+ ascensor.getPasajeros().get(i) +"TIEMPO RELFEXION en t : "+ event_list.getR());
                
                    /* se genera el tiempo de reflexión del pasajero en la planta */
                    event_list.setR(clock + GR());
                    /* indicamos cual es ese tiempo de reflexion al pasajero */
                    ascensor.getPasajeros().get(i).setTiempoReflexion(event_list.getR());
                    ascensor.getPasajeros().get(i).setPisoActual(piso_actual);
                    /* ponemos al pasajero en una lista de reflexion */
                    pasajeros_reflexion.add(ascensor.getPasajeros().get(i));
                
                    /* sacamos el pasajero del ascensor */
                    ascensor.getPasajeros().remove(i);
                    /* al eliminar un elemento de un ArrayList se desplazan todos hacia atras */
                    i--;
                  }                 
                 
             }
             
           
             i++;
         } 
        System.out.println(".................PASAJEROS QUEDARON: " + ascensor.getPasajeros().size() + " i:" + i);
       /* for (int m = 0; m < ascensor.getPasajeros().size();m++){
            System.out.println(ascensor.getPasajeros().get(m));
        }
        * */
     }
     public void mostrar_pasajeros_actuales(){
        System.out.println(" ******PASAJEROS EN ASCENSOR************");
        for (int m = 0; m < ascensor.getPasajeros().size();m++){
            System.out.println(ascensor.getPasajeros().get(m));
        }
        
        
     }
     public void proxima_parada_interior()
     {
         /* miramos los destinos de todos los pasajeros y nos quedamos con el siguiente */
         int i = 0;
         int piso_aux;
         if (ascensor.getPasajeros().size() > 0 ){
            piso_aux = ascensor.getPasajeros().get(0).getPisoDestino();
            while (i < ascensor.getPasajeros().size())
            {
               if (ascensor.getPasajeros().get(i).getPisoDestino() < piso_aux ){
                  piso_aux = ascensor.getPasajeros().get(i).getPisoDestino();
               }
            }
             /* actualizamos la proxima parada */
            ascensor.setProximaParada(piso_aux);
         }
        
     }
     /* si hay algun pasajeros que quiera bajarse en este piso */
     public boolean existe_pasajero_irse()
     {
         int i = 0;
         boolean salir = false;
     
       while (i < ascensor.getPasajeros().size() && !salir)
         {
             
            if (ascensor.getPasajeros().get(i).getPisoDestino() == piso_actual)
             {
                 salir = true;
             }
             i++;
         }
         return salir;
     }
     public  ArrayList<Cola>  getColaConAlguien()
     {
         if (ascensor.getDireccion().equals("ASC"))
         {
            return cola_subida;
         }else{ 
            return cola_bajada;
         }
     }
     public  ArrayList<Cola>  getColaActual()
     {
         if (ascensor.getDireccion().equals("ASC"))
         {
            return cola_subida;
         }else{
            return cola_bajada;
         }
     }
     public void aceptar_pasajeros ()
     {
         /* cola_subida.get(piso_actual).size() mientras haya gente que subir y sean menos de 10 personas en el ascensor */
         while (ascensor.getNumPasajeros() <= MAX_PASAJEROS && getColaActual().get(piso_actual).size()  > 0 )
         {
           
            if (piso_actual==0){
                getColaActual().get(piso_actual).frente().setFinEspera(clock);
                tiempo_espera.add( getColaActual().get(piso_actual).frente().getTiempoEspera());
                System.out.println(" TIEMPO_LLEGADA: "+ getColaActual().get(piso_actual).frente().getTiempoEntrada() +"%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%HE ESPERADO : "+ getColaActual().get(piso_actual).frente().getTiempoEspera());
                
                total_delayed += clock - getColaActual().get(piso_actual).frente().getTiempoEntrada();
                System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$TOTAL DELAYED:" + total_delayed);
            } // no hace falta, es para comprabar una cosa
             
            internas[getColaActual().get(piso_actual).frente().getPisoDestino()] = 1;
            ascensor.getPasajeros().add(getColaActual().get(piso_actual).frente());
            
            System.out.println("salida_ascensor - ENTRAN PASAJEROS  - " + getColaActual().get(piso_actual).frente());   
         
            getColaActual().get(piso_actual).desencolar();  
            number_delayed++;
         }
         /* si al final no ha quedado nadie en la cola, actualizamos la botonera */
         if (getColaActual().get(piso_actual).isEmpty()) {
             
             if (getColaActual().get(piso_actual).getDireccion() == "ASC"){
                 subidas[piso_actual]=0;
                
             }else{
                 bajadas[piso_actual]=0;
                   
             }
         }
         
     }

    /* calcular próxima para del ascensor. 
    *  si un pasajero en otra planta va en la misma dirección que el ascensor 
    *  el ascensor realiza una parada en esa planta para recogerlo
    * */
     public void actualizarDireccion(int p)
     {
    
         if (piso_actual < p){
             ascensor.setDireccion("ASC");
         }else{
             ascensor.setDireccion("DESC");
         }
         if (piso_actual == 0) ascensor.setDireccion("ASC");
         if (piso_actual == MAX_PISOS-1) ascensor.setDireccion("DESC");
     }
     public void actualizarDireccion()
     {
         if (piso_actual == 0) ascensor.setDireccion("ASC");
         if (piso_actual == MAX_PISOS-1) ascensor.setDireccion("DESC");
     }
     
     public void actualizarDireccion(int actual, int destino){
         if (actual < destino)
         {
             ascensor.setDireccion("ASC");
         }else{
             ascensor.setDireccion("DESC");
         }
     }
     public void salida_ascensor()
     {
         System.out.println("     SALIDA ASCENSOR clock: " + clock + " direc:" + ascensor.getDireccion());
         
         if (ascensor.getNumPasajeros() < MAX_PASAJEROS) aceptar_pasajeros();
         
         piso_destino = proxima_parada();
  
         mostrar_botonera(internas, "INTERNAS");  
         mostrar_botonera(subidas, "SUBIDAS");
         mostrar_botonera(bajadas, "BAJADAS"); 
         
         
         System.out.println(" piso actual : " + piso_actual + " piso destino: " +piso_destino+ " direccion: "+ ascensor.getDireccion());
        
         
         if (piso_destino == piso_actual ){
           System.out.println(" salida_ascensor - ME QUEDO EN ESTA PLANTA ");
           /* !!! si nadie me llama me quedo en la planta, pero cuando vuelvo a comprobar que alguien me llama? */
           event_list.setS((clock + 1));
           
        }else{
          /* actualizamos el estado del ascensor para que nadie más entre */
          ascensor.setViajando(true);  

          /* se genera el tiempo de llegada del ascensor al otro piso */
          event_list.setL(clock + Tviaje + (piso_destino - piso_actual) + Tcerrar + Tabrir);
          event_list.setS(INFINITO);
       }       
         
         mostrar_pasajeros_actuales();
         
         System.out.println(" llegada_ascensor t: " + event_list.getL() + " number_delayed: "  + number_delayed);
         System.out.println(" ///////////////////////////////////////");
     }
     
     public int sig_piso(int p, int b[])
     {
         int i = p+1;
         boolean salir = false;
         
         while (i < MAX_PISOS && !salir){
             if (b[i] == 1) salir = true;
             i++;
         }
         if (salir){
             return --i;
         }else{
             return p;//no hay llamadas en los pisos siguientes
         }
     }
     public boolean hay_sig_piso(int p, int b[])
     {
         int i = p+1;
         boolean salir = false;
         
         while (i < MAX_PISOS && !salir){
             if (b[i] == 1) salir = true;
             i++;
         }
         return salir;
     }
     public int ant_piso_cualquier_llamada(int p)
     {
         /* miramos si en los pisos siguientes hay alguna llamada
          * da igual si es para bajar o subir
          * si la hay, actualizamos la direccion del ascensor dependiendo
          * de la dirección del pasajero que está esperando
          */
         int i = p;
         boolean salir = false;
         while (i >= 0 && !salir){
             
             if (subidas[i] == 1){
                 salir = true;
                 ascensor.setDireccion("ASC");
             }else if(bajadas[i] == 1){
                 salir = true;
                 ascensor.setDireccion("DESC");
             }
             --i;
         }
         if (salir){
             return ++i;
         }else{
             return p;//no hay llamadas en los pisos siguientes, devolvemoms la misma planta
         }
     }
     public boolean hay_ant_piso(int p, int b[])
     {
         int i = p-1;
         boolean salir = false;
         while (i >= 0 && !salir){
              
             if (b[i] == 1) salir = true;
             --i;
         }
         return salir;
     }
     public int ant_piso(int p, int b[])
     {
         int i = p-1;
         boolean salir = false;
         while (i >= 0 && !salir){
              
             if (b[i] == 1) salir = true;
             --i;
         }
         if (salir){
             return ++i;
         }else{
             return p;//no hay llamadas en los pisos siguientes, devolvemoms la misma planta
         }
     }

     public boolean hay_llamadas_internas(){
         int i = 0;
         boolean salir = false;
         while ( i < MAX_PISOS && !salir)
         { 
             if (internas[i] == 1){
                 salir = true;
             }
             i++;
         }
         if (salir){
             return true;
         }else{
             return false;
         }
     }
     /* obtenemos la siguiente parada dependiendo si hay peticiones en otras plantas */
     public int proxima_parada()
     {
         
         int i = piso_actual;
         int a;
         int b;
         int next=0;
         
         if (!hay_llamadas_internas()){
                     
            /* si hay llamadas en pisos siguientes EN la misma direccion y con la misma direccion */
            /*
		} else if (estado  && (sig(subida) || sig(bajada))) {
			sube();
		} else if (!estado  && (pre(bajada) || pre(subida))) {
			baja();			
		} else if (!estado  && (sig(bajada) || sig(subida))) {
			sube();
		} else if (estado  && (pre(subida) || pre(bajada))) {
			baja();
             */
            System.out.println("  NO HAY LLAMADAS INTERNTAS");
            if (ascensor.getDireccion().equals("ASC") && (hay_sig_piso(piso_actual,subidas) || hay_sig_piso(piso_actual,bajadas))){
               // System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%1");
                if (hay_sig_piso(piso_actual,subidas)){
                    next = sig_piso(piso_actual,subidas);
                }else{
                    next = sig_piso(piso_actual,bajadas);
                }
            }else if (ascensor.getDireccion().equals("DESC") && (hay_ant_piso(piso_actual,subidas) || hay_ant_piso(piso_actual,bajadas))){
              // System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%2");
                if (hay_ant_piso(piso_actual,bajadas)){
                    next = ant_piso(piso_actual,bajadas);
                }else{
                    next = ant_piso(piso_actual,subidas);
                } 
            }else if(ascensor.getDireccion().equals("DESC") && (hay_sig_piso(piso_actual,subidas) || hay_sig_piso(piso_actual,bajadas))){
               // System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%3");
                if (hay_sig_piso(piso_actual,subidas)){
                    next = sig_piso(piso_actual,bajadas);
                }else{
                    next = sig_piso(piso_actual,subidas);
                }
            }else if(ascensor.getDireccion().equals("ASC") && (hay_ant_piso(piso_actual,subidas) || hay_ant_piso(piso_actual,bajadas))){
                //System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%4");
                if (hay_ant_piso(piso_actual,bajadas)){
                    next = ant_piso(piso_actual,bajadas);
                    ascensor.setDireccion("DESC");
                }else{
                    next = ant_piso(piso_actual,subidas);
                } 
                //System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%% "+ next);
            }else{
                next = piso_actual;
            }
         
    
         }else{
            /* si hay llamadas internas, miramos si entre el viaje hasta el destino del pasajero del ascensor
             * hay otro pasajero con la misma dirección, en algun piso por el que debemos pasar 
             */
             System.out.println(" HAY LLAMADAS INTERNTAS");
            if (ascensor.getDireccion().equals("ASC"))
            {

                a = sig_piso(piso_actual,internas);
                b = sig_piso(piso_actual,subidas);
                if (a == piso_actual) a = b;
                if (b == piso_actual) b = a;
                //if (a == 0) a = b;
                //if (b == 0) b = a;
                next = Math.min(a,b);
            }
            if (ascensor.getDireccion().equals("DESC"))
            {

                a = ant_piso(piso_actual,internas);
                b = ant_piso(piso_actual,bajadas);
                if (a == piso_actual) a = b;
                if (b == piso_actual) b = a;
                next = Math.max(a,b);
            }
          
         }
           actualizarDireccion(next);
         return next;
          
     }
     
     public void actualizarPisoAscensor()
     {
         piso_actual = piso_destino;
     }
     
     public void temporizacion()         
     {
         /* min (event_list()) */
         
         if (event_list.getA() <= event_list.getL() && event_list.getA() <= event_list.getS() && event_list.getA() <= event_list.getR()){
             
             clock = event_list.getA();
             
         }else if (event_list.getL() <= event_list.getA() && event_list.getL() <= event_list.getS() && event_list.getL() <= event_list.getR()){
             
             clock = event_list.getL();
             
         }else if (event_list.getS() <= event_list.getA() && event_list.getS() <= event_list.getL() && event_list.getS() <= event_list.getR()){
            
             clock = event_list.getS();
         
         }else{
             
             clock = event_list.getR();
             
         }
         
     }
     public void principal()
     {    
         inicializar();
         while (number_delayed < 50)
         {  
            temporizacion();
            //System.out.println("clock "+ clock);
            if (clock == event_list.getA())
            {
                orden.add("llegada_pasajero();");
                ordenclock.add(clock);
                llegada_pasajero();
            
            }else if( clock == event_list.getL()){
                
                orden.add("llegada_ascensor();");
               ordenclock.add(clock);
                llegada_ascensor();
                
            }else if (clock == event_list.getS()){
                orden.add("salida_ascensor();");
                ordenclock.add(clock);
                
                salida_ascensor();
                
            }else{
                orden.add("fin_reflexion();");
               ordenclock.add(clock);
                fin_reflexion();
                
            }
         }
         visualizar_resultados();
        
     }
     public void visualizar_resultados()
     {
         /*for (int i = 0; i< orden.size();i++){
             System.out.println(orden.get(i));
         }*/
         System.out.println("area de espera acumulada: "+ area_under_q);
         System.out.println("tiempo medio de espera acumulado: "+ area_under_q/100);
        // System.out.println("tiempo medio de espera acumulado: "+ area_under_q/clock);
         float b=0;
         for (int p = 0; p<tiempo_espera.size();p++){
             b = (float) tiempo_espera.get(p) + b;
             //System.out.println(b);
         }
         System.out.println("tiempo medio de espera acumulado: "+ b/tiempo_espera.size());
         System.out.println("total delayed: " + total_delayed);
         System.out.println("media total delayed: " + total_delayed/5);
          /*for (int i = 0; i< ordenclock.size();i++){
             System.out.println(ordenclock.get(i));
         }*/
         
     }
     public void prueba()
     {
         
         ArrayList prueba = new ArrayList<>();
         
         prueba.add("cero");
         prueba.add("uno");
         prueba.add("dos");
         prueba.add("tres");
        weibull = new WeibullDistribution(2, 22);
   
       
        ExponentialDistribution exp = new ExponentialDistribution(0.016);
        for (int i = 0; i< 50;i++)
        {
   
           // System.out.println(determinarPisoProbabilidad(5) + "\n");
           // System.out.println(determinar_piso(5));
           // System.out.println(poisson.sample());
          // System.out.println(Math.round(exp.sample()*1000));
          //System.out.println(randomInterval(1,6));
          //System.out.println(determinarPisoProbabilidad(1));
           System.out.println(GA());
           
           //System.out.println(GR());

        }
     }
     public void test()
     {
         inicializar();


     }
     public static void main(String[] args) {

        // TODO code application logic here         ¡
         Main m = new Main();
         //m.test();
        m.prueba();
        //m.principal();
        // m.inicializar();
         //m.prueba();

    }
/*
 * POSIBLES CASOS QUE TENEMOS EN CUENTA:
 * Si un pasajero termina su tiempo de reflexion
 * se encuentra con ascensor en su planta, pero con dirección distinta, sin nadie dentro pero
 * tiene una llamada en un piso con su misma dirección, entonces
 * Lo que hacemos es mirar desde el piso actual en que piso tengo una llamada,
 * da igual si es de bajada o subida, y me voy al más cercano.
 * En este caso, se subiría el pasajero que sale del tiempo de reflexión aunque tenga direccion
 * distinta, ya que está en el mismo piso donde está el ascensor. (en la vida real es así)
 */
    
}
